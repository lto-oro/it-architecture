import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KeyValueMapper;

public class MyMapper implements  KeyValueMapper<String, String, KeyValue<String, String>>  {

	@Override
	public KeyValue<String, String> apply(String key, String value) {
		
		System.out.println("MyMapper    got the Event: ["+key+"]->["+value+"]");

		// create a new event that contains the previous event data plus some sample text
		return new KeyValue<String, String>(key, value + " went through Mapper"); 
	}

}
