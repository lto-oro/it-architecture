public final class DelayProcessing {

	public static final int SIGNIFICANT_DELAY_SECONDS = 180;

	private DelayProcessing() {
	}

	public interface DelayLookup {
		int requestDelay(String deliveryId, Utils.GpsPos position);
	}

	public static final class RouteTiming {
		public final String id;
		public final String message;

		RouteTiming(String id, String message) {
			this.id = id;
			this.message = message;
		}
	}

	public static RouteTiming toRouteTiming(String kafkaKey, String positionEvent, DelayLookup lookup) {
		if (positionEvent == null) {
			return null;
		}

		Utils.GpsPos position = Utils.extractCoordinates(positionEvent);
		if (position == null) {
			return null;
		}

		String deliveryId = deliveryId(kafkaKey, position);
		if (deliveryId == null) {
			return null;
		}

		int delay = lookup.requestDelay(deliveryId, position);
		return new RouteTiming(deliveryId, delayMessage(deliveryId, delay));
	}

	public static String toSignificantDelay(String routeTimingEvent) {
		if (routeTimingEvent == null) {
			return null;
		}

		Integer delay = Utils.extractDelay(routeTimingEvent);
		if (delay == null || delay <= SIGNIFICANT_DELAY_SECONDS) {
			return null;
		}
		return routeTimingEvent.trim();
	}

	private static String deliveryId(String kafkaKey, Utils.GpsPos position) {
		if (position.id != null && !position.id.isBlank()) {
			return position.id;
		}
		if (kafkaKey != null && !kafkaKey.isBlank()) {
			return kafkaKey.trim();
		}
		return null;
	}

	private static String delayMessage(String deliveryId, int delay) {
		return "id: " + deliveryId + ", delay: " + delay;
	}
}
