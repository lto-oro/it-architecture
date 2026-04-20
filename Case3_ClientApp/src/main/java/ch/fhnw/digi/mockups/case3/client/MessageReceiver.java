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
	 * Empfaengt neue Auftraege vom Topic "dispo.jobs.new".
	 * Das Fremdsystem publiziert alle 2 Sekunden einen neuen Job.
	 * Jeder verbundene Client (Subscriber) erhaelt eine Kopie (Publish-Subscribe Pattern).
	 */
	@JmsListener(destination = "dispo.jobs.new", containerFactory = "myFactory")
	public void receiveNewJob(JobMessage job) {
		System.out.println("Neuer Job empfangen: " + job);
		ui.addJobToList(job);
	}

	/**
	 * Empfaengt Auftragszuweisungen vom Topic "dispo.jobs.assignments".
	 * Nach einer Anfrage ueber die Queue antwortet die Disposition hier
	 * mit der Zuweisung. Alle Clients erhalten diese Nachricht, damit
	 * der zugewiesene Job bei allen aus der offenen Liste entfernt wird.
	 */
	@JmsListener(destination = "dispo.jobs.assignments", containerFactory = "myFactory")
	public void receiveAssignment(JobAssignmentMessage assignment) {
		System.out.println("Zuweisung empfangen: Job " + assignment.getJobnumber()
				+ " an " + assignment.getAssignedEmployee());
		ui.assignJob(assignment);
	}

	/**
	 * Factory fuer JMS Listener Container.
	 * setPubSubDomain(true) konfiguriert die Factory fuer Topics (Publish-Subscribe).
	 * Beide Listener oben hoeren auf Topics, daher ist diese Konfiguration korrekt.
	 */
	@Bean
	public DefaultJmsListenerContainerFactory myFactory(ConnectionFactory connectionFactory,
			DefaultJmsListenerContainerFactoryConfigurer configurer) {
		DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();

		configurer.configure(factory, connectionFactory);
		factory.setPubSubDomain(true);
		factory.setMessageConverter(jacksonJmsMessageConverter());

		return factory;
	}

	@Bean // Serialisiert Nachrichteninhalt zu/von JSON mittels TextMessage
	public MessageConverter jacksonJmsMessageConverter() {
		MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
		converter.setTargetType(MessageType.TEXT);
		converter.setTypeIdPropertyName("_type");
		return converter;
	}

}
