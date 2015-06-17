package care.siren.beanviewer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.util.Log;
import android.os.Handler;

public class SimpleStorageWorker implements Handler.Callback {
	String TAG = this.getClass().getSimpleName();
	private File file;
	private FileWriter fr = null;
    private BufferedWriter br = null;
    private final int buffer_size = 4*8192;
    private Context _context;
    private boolean _writable = false;
    public static final int MSG_OPEN = 0;
    public static final int MSG_WRITE = 1;
    public static final int MSG_CLOSE = 2;
    
    public SimpleStorageWorker(Context context) throws Exception{
    	_context = context;
    }
    
	@Override
	public boolean handleMessage(Message msg) {
		switch(msg.what) {
		case MSG_OPEN:
			newFile(msg.getData().getString("Prefix"));
			break;
		case MSG_WRITE:
			Bundle b = msg.getData();
			store(b.getDoubleArray("Time"), b.getIntArray("Data"), b.getInt("num_sample"), b.getInt("num_channel"));
			break;
		case MSG_CLOSE:
			close();
			break;
		default:
			break;
		}
			
		return false;
	}

	public void store(double[] timeArray, int[] dataArray, int num_sample, int num_channel) {
		if (_writable){
			double[] data = new double[num_channel];
			for (int i = 0; i < num_sample; i++) {
				for (int n = 0; n < num_channel; n++){
					data[n] = (double) dataArray[i*num_channel + n];
				}
				write(data, timeArray[i], num_channel);
			}
		}
	}
	
	public void newFile(String prefix){
		if (!_writable){
			if (isExternalStorageWritable()){
				String currentDateandTime = new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.GERMANY).format(new Date());
				file = new File(_context.getExternalFilesDir(null), prefix + " " + currentDateandTime + ".csv");
				Log.d(TAG, "Opening new file: " + file.getAbsolutePath());
				try {
					fr = new FileWriter(file, true); // Append in case of non-continuous sampling
					br = new BufferedWriter(fr, buffer_size);
					_writable = true;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public void write(double[] data, double time, int chanNum){
		if (_writable){
			try {
				//Log.d(TAG, "Saving data to: " + file.getAbsolutePath());
				br.write(String.format("%f", time));
				for (int n = 0; n < chanNum; n++)
					br.write(String.format(";%f",data[n])); // TODO Change the format to something better
				br.write("\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void close(){
		if (file != null){ // File never opened
			_context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
			try {
				br.close();
				fr.close();
				_writable = false;
			} catch (IOException e) {
				e.printStackTrace();
			}
			file = null;
		}
	}
	
	public boolean writable(){
		return _writable;
	}
	
    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        Log.e("SimpleStorage", "External storage is not writable");
        return false;
    }
}
