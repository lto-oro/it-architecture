package ch.fhnw.digi.mockups.case3;

import java.time.LocalDateTime;

public class JobMessage {

	public enum JobType {
		Maintanence, Repair
	}

	private String jobnumber;
	private String customernumber;
	private String description;
	private String region;
	private String scheduledDateTime;
	private JobType type;

	public String getJobnumber() {
		return jobnumber;
	}

	public void setJobnumber(String jobnumber) {
		this.jobnumber = jobnumber;
	}

	public String getCustomernumber() {
		return customernumber;
	}

	public void setCustomernumber(String customernumber) {
		this.customernumber = customernumber;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getScheduledDateTime() {
		return scheduledDateTime;
	}

	public void setScheduledDateTime(String scheduledDateTime) {
		this.scheduledDateTime = scheduledDateTime;
	}

	public JobType getType() {
		return type;
	}

	public void setType(JobType type) {
		this.type = type;
	}

	@Override
	public String toString() {
		return "Job " + jobnumber + "  (" + description + ")  Geplant für  " + scheduledDateTime + "  in Region "
				+ region;
	}
}
