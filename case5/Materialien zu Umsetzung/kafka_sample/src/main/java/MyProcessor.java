

import org.apache.kafka.streams.kstream.ForeachAction;

public class MyProcessor implements ForeachAction<String, String>{

	@Override
	public void apply(String key, String value) {
		System.out.println("MyProcessor got the Event K:"+key+"   V:"+value);
	}

}
