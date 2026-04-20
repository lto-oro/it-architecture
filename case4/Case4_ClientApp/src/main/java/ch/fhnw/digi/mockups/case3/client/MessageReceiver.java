package ch.fhnw.digi.mockups.case3.client;

import javax.jms.ConnectionFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;
import org.springframework.stereotype.Component;

import ch.fhnw.digi.mockups.case3.JobMessage;
import ch.fhnw.digi.mockups.case3.JobAssignmentMessage;

@Component
public class MessageReceiver {

	@Autowired
	private UI ui;

	/**
	 * Routine-Aufträge aus der Queue "group4.jobs.new"
	 * (publiziert vom Mule-ESB-Flow, Content-Based Routing "type == Maintanence").
	 * Queue -> Point-to-Point -> queueFactory mit setPubSubDomain(false).
	 */
	@JmsListener(destination = "group4.jobs.new", containerFactory = "queueFactory")
	public void receiveNewJob(JobMessage job) {
		System.out.println("[NEW] Routine-Job empfangen: " + job);
		ui.addJobToList(job);
	}

	/**
	 * Dringende Reparaturaufträge aus der Queue "group4.jobs.urgent"
	 * (publiziert vom Mule-ESB-Flow, Content-Based Routing "type == Repair").
	 */
	@JmsListener(destination = "group4.jobs.urgent", containerFactory = "queueFactory")
	public void receiveUrgentJob(JobMessage job) {
		System.out.println("[URGENT] Reparatur-Job empfangen: " + job);
		ui.addJobToList(job);
	}

	/**
	 * Auftragszuweisungen vom Topic "dispo.jobs.assignments".
	 * Unverändert aus Case 3 - Pub/Sub-Rückkanal der Disposition.
	 */
	@JmsListener(destination = "dispo.jobs.assignments", containerFactory = "topicFactory")
	public void receiveAssignment(JobAssignmentMessage assignment) {
		System.out.println("Zuweisung empfangen: Job " + assignment.getJobnumber()
				+ " an " + assignment.getAssignedEmployee());
		ui.assignJob(assignment);
	}

	/**
	 * Factory für Queue-Listener (Point-to-Point).
	 * Neu in Case 4: Die vom Mule-Flow publizierten Jobs landen auf Queues,
	 * nicht mehr auf dem Case-3-Topic "dispo.jobs.new".
	 */
	@Bean
	public DefaultJmsListenerContainerFactory queueFactory(ConnectionFactory connectionFactory,
			DefaultJmsListenerContainerFactoryConfigurer configurer) {
		DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
		configurer.configure(factory, connectionFactory);
		factory.setPubSubDomain(false);
		factory.setMessageConverter(jacksonJmsMessageConverter());
		return factory;
	}

	/**
	 * Factory für Topic-Listener (Publish/Subscribe).
	 * Bleibt für den Case-3-Rückkanal "dispo.jobs.assignments" aktiv.
	 */
	@Bean
	public DefaultJmsListenerContainerFactory topicFactory(ConnectionFactory connectionFactory,
			DefaultJmsListenerContainerFactoryConfigurer configurer) {
		DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
		configurer.configure(factory, connectionFactory);
		factory.setPubSubDomain(true);
		factory.setMessageConverter(jacksonJmsMessageConverter());
		return factory;
	}

	@Bean // Wandelt JSON in JAVA-Objekt um
	public MessageConverter jacksonJmsMessageConverter() {
		MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
		converter.setTargetType(MessageType.TEXT);
		converter.setTypeIdPropertyName("_type");
		return converter;
	}

}
