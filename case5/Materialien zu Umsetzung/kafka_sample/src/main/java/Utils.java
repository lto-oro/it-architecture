

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

	static class GpsPos {
		String time; // keeping the time as a string to avoid any conversions for simplicity
		float lat;
		float lon;
	}

	// Use a Regular Expression to split the event data into the relevant components
	// example Message:
	// id: 1, time: 2023-10-11 08:38:56.621042223 +02:00, lat: 47.351543, lon:
	// 7.900175
	private static Pattern p = Pattern.compile("^id: ([0-9]+), time: (.+?), lat: ([0-9.]+?), lon: ([0-9.]+?)$");

	public static GpsPos extractCoordinates(String line) {
		Matcher m = p.matcher(line.trim());
		GpsPos res = new GpsPos();

		if (m.find()) {
			res.time = m.group(2);
			res.lat = Float.parseFloat(m.group(3));
			res.lon = Float.parseFloat(m.group(4));

			return res;
		}
		return null;

	}

	public static int requestDelay(String key, GpsPos pos) {

		// Note: this is a very basic way of connecting to a REST service to keep it
		// simple. In reality it would be strongly advisable to use a client library
		// like Jersey or similar. -- also this method has quite bad error handling...

		try {

			// build a URL with all our parameters
			URL url = new URL("http://192.168.111.11:8080/route/" + key + "?time="
					+ URLEncoder.encode(pos.time, StandardCharsets.UTF_8) + "&lat=" + pos.lat + "&lon=" + pos.lon);

			System.out.println("Requesting: "+url);
			
			// make a request to the service
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("GET");
			con.connect();

			// handle the result --> which is the delay in minutes
			int status = con.getResponseCode();
			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			StringBuffer content = new StringBuffer();
			while ((inputLine = in.readLine()) != null) {
				content.append(inputLine);
			}
			in.close();
			con.disconnect();

			// parse string to integer...
			return Integer.parseInt(content.toString());

		} catch (Exception e) {
			e.printStackTrace();
			return -100000; // dummy error value
		}

	}

	// Use a Regular Expression to split the event data 
	// Example Message:
	// 	id: 1, delay: 123
	private static Pattern p2 = Pattern.compile("^id: ([0-9]+), delay: ([0-9-]+?)$");
	
	public static Integer extractDelay(String line) {
		Matcher m = p2.matcher(line.trim());
		if (m.find()) {
			return Integer.parseInt(m.group(2));
		}
		return null;
	}

}
