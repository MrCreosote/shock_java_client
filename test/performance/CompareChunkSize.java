package performance;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.nocrala.tools.texttablefmt.Table;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.shock.client.BasicShockClient;
import us.kbase.shock.client.ShockNode;

// totally useless now, client doesn't use chunks

public class CompareChunkSize {
	
	public static void main(String[] args) throws Exception {
		String user = args[0];
		String pwd = args[1];
		new CompareChunkSize(user, pwd, "http://localhost:7044",
				//dog slow below 100k
				Arrays.asList(500000, 1000000, 2000000, 5000000, 10000000,
						20000000, 50000000, 100000000));
	}

	private static final int REPS = 10;
	private static final int DATA_SIZE = 500000001;
	private final byte[] data;
	private final AuthToken token;
	private final URL shockURL;
	
	public CompareChunkSize(String user, String pwd,
			String shockURL, List<Integer> chunkSizes)
					throws Exception {
		System.out.println("Testing shock read/write speeds, N=" + REPS);
		System.out.println("logging in " + user);
		this.token = AuthService.login(user, pwd).getToken();
		this.shockURL = new URL(shockURL);
		data = new byte[DATA_SIZE];
		for (int i = 0; i < data.length; i++) {
			data[i] = (byte) 0xAA; //whatever
		}
		System.out.println("Testing shock java client against " + shockURL);
		System.out.println(String.format("file size: %,dB", data.length));
		Map<Integer, Perf> results = new HashMap<Integer, CompareChunkSize.Perf>();
		for (Integer chunksize: chunkSizes) {
			results.put(chunksize, measurePerformance(chunksize));
		}
		List<Integer> sorted = new ArrayList<Integer>(results.keySet());
		Collections.sort(sorted);
		
		Table tble = new Table(5);
		tble.addCell("Chunksize");
		tble.addCell("write (s)");
		tble.addCell("write (MBps)");
		tble.addCell("read (s)");
		tble.addCell("read (MBps)");
		for (Integer i: sorted) {
			Perf p = results.get(i);
			tble.addCell("" + i);
			double wmean = p.getAverageWritesInSec();
			double rmean = p.getAverageReadsInSec();
			double wmbps = data.length / wmean / 1000000;
			double rmbps = data.length / rmean / 1000000;
			
			tble.addCell(String.format("%,.4f +/- %,.4f", wmean, p.getStdDevWritesInSec(wmean)));
			tble.addCell(String.format("%,.3f", wmbps));
			tble.addCell(String.format("%,.4f +/- %,.4f", rmean, p.getStdDevReadsInSec(rmean)));
			tble.addCell(String.format("%,.3f", rmbps));
		}
		System.out.println(tble.render());
	}
	
	private BasicShockClient getClient(int chunksize) throws Exception {
		Field chunk = BasicShockClient.class.getDeclaredField("CHUNK_SIZE");
		chunk.setAccessible(true);
		chunk.setInt(BasicShockClient.class, chunksize);
		return new BasicShockClient(shockURL, token);
	}
	
	private Perf measurePerformance(int chunksize) throws Exception {
		System.out.println(String.format(
				"Measuring speed with chunksize of %,dB", chunksize));
		BasicShockClient bsc = getClient(chunksize);
		List<Long> writes = new LinkedList<Long>();
		List<Long> reads = new LinkedList<Long>();
		for (int i = 0; i < REPS; i++) {
			System.out.print(i + " ");
			long start = System.nanoTime();
			ShockNode sn = bsc.addNode(new ByteArrayInputStream(data), DATA_SIZE, "foo", "UTF-8");
			writes.add(System.nanoTime() - start);

			start = System.nanoTime();
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			sn.getFile(baos);
			baos.toByteArray();
			reads.add(System.nanoTime() - start);
			sn.delete();
		}
		System.out.println();
		return new Perf(writes, reads);
	}
	
	private class Perf {
		
		private final static double nanoToSec = 1000000000.0;
		
		private final List<Long> writes;
		private final List<Long> reads;
		
		public Perf(List<Long> writes, List<Long> reads) {
			this.writes = writes;
			this.reads = reads;
		}
		
		public double getAverageWritesInSec() {
			return mean(writes)/ nanoToSec;
		}
		
		public double getAverageReadsInSec() {
			return mean(reads) / nanoToSec;
		}
		
		private double mean(List<Long> nums) {
			double sum = 0;
			for (Long n: nums) {
				sum += n;
			}
			return sum / nums.size();
		}
		
		public double getStdDevWritesInSec(double mean) {
			return stddev(mean, writes, false);
		}
		
		public double getStdDevReadsInSec(double mean) {
			return stddev(mean, reads, false);
		}
		
		@SuppressWarnings("deprecation")
		private double stddev(double mean, List<Long> values, boolean population) {
			if (values.size() < 2) {
				return Double.NaN;
			}
			final double pop = population ? 0 : -1;
			double accum = 0;
			for (Long d: values) {
				accum += Math.pow(new Double(d) / nanoToSec - mean, 2);
			}
			return Math.sqrt(accum / (values.size() + pop));
		}
		
	}
}
