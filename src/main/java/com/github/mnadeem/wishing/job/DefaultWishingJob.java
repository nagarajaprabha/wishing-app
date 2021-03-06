package com.github.mnadeem.wishing.job;

import static com.github.mnadeem.wishing.Constants.DATE_PATTERN_MAIL_EXPIRE;
import static com.github.mnadeem.wishing.Constants.DEFAULT_FROM_EMAIL_ADDRESS;
import static com.github.mnadeem.wishing.Constants.EXTENSION_JPG;
import static com.github.mnadeem.wishing.Constants.PROPERTY_NAME_ANNIVERSARY_DEFAULT_IMAGE_COUNT;
import static com.github.mnadeem.wishing.Constants.PROPERTY_NAME_ANNIVERSARY_YEARS_COUNT;
import static com.github.mnadeem.wishing.Constants.PROPERTY_NAME_BELATED_PREFIX;
import static com.github.mnadeem.wishing.Constants.PROPERTY_NAME_BIRTHDAY_IMAGE_COUNT;
import static com.github.mnadeem.wishing.Constants.PROPERTY_NAME_IMAGE_BASE_PATH;
import static com.github.mnadeem.wishing.Constants.PROPERTY_NAME_IMAGE_EXTENSION;
import static com.github.mnadeem.wishing.Constants.PROPERTY_NAME_MAIL_EXPIRE_AFTER_DAYS;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

import javax.mail.MessagingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.github.mnadeem.wishing.service.EmailService;
import com.github.mnadeem.wishing.service.WishingDataService;
import com.github.mnadeem.wishing.service.data.Mail;
import com.github.mnadeem.wishing.service.data.Wish;

/**
 * 
 * @author Mohammad Nadeem (coolmind182006@gmail.com)
 *
 */
@Component
public class DefaultWishingJob implements WishingJob {

	private static Logger logger = LoggerFactory.getLogger(DefaultWishingJob.class);

	@Autowired
	private WishingDataService dataService;
	@Autowired
	private EmailService emailService;
	@Autowired
	private Environment env;

	@Override
	public void ping() throws Exception {
		logger.trace("Ping");		
	}

	@Override
	public void processWishes(LocalDate date) throws Exception {
		logger.debug("Job running for {}", date);
		int wishCount = dataService.forEach(date, this::sendEmail);
		logger.debug("Job finished for {}, processed {} wish(es)", date, wishCount);
	}

	private void sendEmail(Wish wish, LocalDate date) {
		if (wish.shouldWish() && isEnabled(wish)) {
			try {				
				Mail buildMail = buildMail(wish, date);
				this.emailService.send(buildMail);
			} catch (MessagingException e) {
				logger.error("Error Sending message", e);
			}

		} else {
			if (logger.isTraceEnabled()) {
				logger.trace("Wish not applicable / enabled for {}", wish);
			}
		}
	}

	private boolean isEnabled(Wish wish) {
		StringBuilder key = new StringBuilder();
		key.append("app.mailer.");
		if (wish.isBirthday()) {
			key.append("birthday");
		} else {
			key.append("anniversary");
		}
		key.append(".enabled");
		return env.<Boolean>getProperty(key.toString(), Boolean.class, Boolean.TRUE);
	}

	private Mail buildMail(Wish wish, LocalDate date) {
		String from = env.<String>getProperty("app.name" + wish.getPartition() + ".from", String.class, DEFAULT_FROM_EMAIL_ADDRESS);
		String cc = env.<String>getProperty("app.name" + wish.getPartition() + ".cc", String.class, "");
		Mail mail = new Mail();
		mail.setTo(wish.getEmail());
		mail.setFrom(from);
		mail.setSubject(buildSubject(wish, date));
		mail.setContent(buildContent(wish));
		mail.setImage(buildImage(wish));
		mail.setCc(cc);
		mail.setExpire(getExpire());

		return mail;
	}

	private String buildImage(Wish wish) {
		String image = null;
		if (wish.isBirthday()) {
			image = getBirthDayImageName();
		} else {
			image = getAnniversaryImageName(wish);
		}
		return image;
	}

	private String getAnniversaryImageName(Wish wish) {
		Integer anniversarycount = env.<Integer>getProperty(PROPERTY_NAME_ANNIVERSARY_YEARS_COUNT, Integer.class, 1);
		int anniversary = wish.getYears();

		Integer maxImagesCount = getImagesCount(anniversarycount, anniversary);

		StringBuilder builder = new StringBuilder();
		builder.append(getBaseImagePath()).append("/anniversay/");
		if (anniversary > anniversarycount) {
			builder.append("default/");
		} else {
			builder.append(anniversary).append("/");
		}
		builder.append(randomNumber(maxImagesCount)).append(getImageExtension());
		return builder.toString();
	}

	private int getImagesCount(int anniversarycount, int anniversary) {
		String key = "app.anniversary.year" + anniversary + ".image_count";
		if (anniversary > anniversarycount) {
			key = PROPERTY_NAME_ANNIVERSARY_DEFAULT_IMAGE_COUNT;
		}
		Integer count = env.<Integer>getProperty(key, Integer.class, 1);
		return count;
	}

	private String getBirthDayImageName() {
		Integer count = env.<Integer>getProperty(PROPERTY_NAME_BIRTHDAY_IMAGE_COUNT, Integer.class, 1);
		return getBaseImagePath() + "/birthday/" + randomNumber(count) + getImageExtension();
	}

	private int randomNumber(int max) {
		int min = 1;
		return ThreadLocalRandom.current().nextInt(min, max + 1);
	}

	private String buildContent(Wish wish) {
		return "";
	}

	private String buildSubject(Wish wish, LocalDate date) {
		StringBuilder subject = new StringBuilder();
		subject.append(belatedPrefix(date)).append(wish.getWish()).append(" ").append(wish.getName());
		if (wish.isBirthday()) {
			subject.append("!");
		} else {
			subject.append(", ").append(wish.getYearsMessage()).append(" Completed!");
		}

		return subject.toString();
	}

	private String belatedPrefix(LocalDate date) {
		if (date.isBefore(LocalDate.now())) {
			return env.<String>getProperty(PROPERTY_NAME_BELATED_PREFIX, String.class, "");
		}
		return "";
	}

	private String getImageExtension() {
		return "." + env.<String>getProperty(PROPERTY_NAME_IMAGE_EXTENSION, String.class, EXTENSION_JPG);
	}

	private String getBaseImagePath() {
		return env.<String>getProperty(PROPERTY_NAME_IMAGE_BASE_PATH, String.class, "classpath:data/images");
	}

	private String getExpire() {
		Integer expireDays = env.<Integer>getProperty(PROPERTY_NAME_MAIL_EXPIRE_AFTER_DAYS, Integer.class);
		String result = null;
		if (expireDays != null && expireDays > 0) {
			ZonedDateTime expireDate = ZonedDateTime.now().plusDays(expireDays);
	        DateTimeFormatter format = DateTimeFormatter.ofPattern(DATE_PATTERN_MAIL_EXPIRE);  
	        result = expireDate.format(format);  
		}
		return result;
	}
}
