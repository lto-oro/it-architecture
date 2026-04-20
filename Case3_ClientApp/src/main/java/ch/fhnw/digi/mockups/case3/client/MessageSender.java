package ch.fhnw.digi.mockups.case3.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.stereotype.Component;

import ch.fhnw.digi.mockups.case3.JobMessage;
import ch.fhnw.digi.mockups.case3.JobRequestMessage;

@Component
public class MessageSender {

	@Autowired
	private JmsTemplate jmsTemplate;
	@Autowired
	private MessageConverter jacksonJmsMessageConverter;

	/**
	 * Sendet eine Auftragszuteilungs-Anfrage an die Disposition.
	 *
	 * Die Nachricht wird an die Queue "dispo.jobs.requestAssignment" gesendet
	 * (Point-to-Point Channel). setPubSubDomain(false) ist wichtig, da es sich
	 * um eine Queue handelt und nicht um ein Topic.
	 *
	 * Die Disposition verarbeitet die Anfrage und antwortet ueber das
	 * Topic "dispo.jobs.assignments" (empfangen in MessageReceiver).
	 */
	void requestJobFromDispo(JobMessage job) {
		JobRequestMessage request = new JobRequestMessage();
		request.setJobnumber(job.getJobnumber());
		request.setRequestingEmployee("Gruppe4");

		jmsTemplate.setMessageConverter(jacksonJmsMessageConverter);
		jmsTemplate.setPubSubDomain(false); // Queue, nicht Topic!
		jmsTemplate.convertAndSend("dispo.jobs.requestAssignment", request);

		System.out.println("Auftrag angefordert: " + job.getJobnumber());
	}

}
