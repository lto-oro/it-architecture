import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class DelayProcessingTest {

	private static final String POSITION_EVENT =
			"id: 6, time: 2024-04-01T12:01:21.123647949+02:00, lat: 47.35203, lon: 7.905917";

	@Test
	void stage1ConvertsDriverPositionToRouteTimingEvent() {
		AtomicReference<String> requestedId = new AtomicReference<>();

		DelayProcessing.RouteTiming result = DelayProcessing.toRouteTiming(null, POSITION_EVENT, (deliveryId, position) -> {
			requestedId.set(deliveryId);
			return 231;
		});

		assertEquals("6", requestedId.get());
		assertEquals("6", result.id);
		assertEquals("id: 6, delay: 231", result.message);
	}

	@Test
	void stage2OnlyReturnsDashboardEventForSignificantDelay() {
		assertEquals("id: 6, delay: 181", DelayProcessing.toSignificantDelay("id: 6, delay: 181"));
		assertNull(DelayProcessing.toSignificantDelay("id: 6, delay: 180"));
		assertNull(DelayProcessing.toSignificantDelay("id: 6, delay: -100000"));
		assertNull(DelayProcessing.toSignificantDelay("invalid"));
	}
}
