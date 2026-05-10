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
		String id;
		String time;
		float lat;
		float lon;
	}

	private static Pattern p = Pattern.compile("^id: ([0-9]+), time: (.+?), lat: ([0-9.]+?), lon: ([0-9.]+?)$");

	public static GpsPos extractCoordinates(String line) {
		Matcher m = p.matcher(line.trim());
		GpsPos res = new GpsPos();

		if (m.find()) {
			res.id = m.group(1);
			res.time = m.group(2);
			res.lat = Float.parseFloat(m.group(3));
			res.lon = Float.parseFloat(m.group(4));

			return res;
		}
		return null;
	}

	public static int requestDelay(String key, GpsPos pos) {
		try {
			URL url = new URL("http://192.168.111.11:8080/route/" + key + "?time="
					+ URLEncoder.encode(pos.time, StandardCharsets.UTF_8) + "&lat=" + pos.lat + "&lon=" + pos.lon);

			System.out.println("Requesting: " + url);

			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("GET");
			con.connect();

			con.getResponseCode();
			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			StringBuffer content = new StringBuffer();
			while ((inputLine = in.readLine()) != null) {
				content.append(inputLine);
			}
			in.close();
			con.disconnect();

			return Integer.parseInt(content.toString());
		} catch (Exception e) {
			e.printStackTrace();
			return -100000;
		}
	}

	private static Pattern p2 = Pattern.compile("^id: ([0-9]+), delay: ([0-9-]+?)$");

	public static Integer extractDelay(String line) {
		Matcher m = p2.matcher(line.trim());
		if (m.find()) {
			return Integer.parseInt(m.group(2));
		}
		return null;
	}
}
