package care.siren.beanviewer;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.CircularArray;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.util.ChartUtils;
import lecho.lib.hellocharts.view.LineChartView;


public class MainActivity extends ActionBarActivity {
    private String TAG = "BeanViewer";
    BeanService beanService;
    boolean mBound = false;
    TextView scratchText;
    TextView beanName;

    // Chart
    private LineChartView chart;
    private LineChartData data;
    private int numberOfLines = 0;
    private int maxNumberOfLines = 10;
    private int numberOfPoints = 30;
    List<CircularArray<Integer>> temperatures;

    // Storage
    private SimpleStorageWorker storageWorker;
    private Handler storageHandler;
    private boolean store_data = false;
    private boolean file_open = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        beanName = (TextView) findViewById(R.id.connected_ble_name);
        scratchText = (TextView) findViewById(R.id.receivedData);
        chart = (LineChartView) findViewById(R.id.chart);

        temperatures = new ArrayList<>();

        // New handler thread for storage
        HandlerThread handlerThread = new HandlerThread("storageThread");
        handlerThread.start();
        Looper looper = handlerThread.getLooper();

        // Create an instance of the class that will handle the messages that are posted
        //  to the Handler
        try {
            storageWorker = new SimpleStorageWorker(this);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // Create a Handler and give it the worker instance to handle the messages
        storageHandler = new Handler(looper, storageWorker);
    }

    @Override
    protected void onStart(){
        super.onStart();
        Intent intent = new Intent(this, BeanService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BeanService.SCRATCH_RECV);
        intentFilter.addAction(BeanService.BLE_CONNECTED);
        intentFilter.addAction(BeanService.BLE_DISCONNECTED);
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(mBound){
            unbindService(mConnection);
            mBound = false;
        }

        if(store_data)
            closeFile();
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()){
                case BeanService.SCRATCH_RECV:
                    int[] result = intent.getIntArrayExtra("Data");
                    int idx = 1;

                    // Cut away the decimals
//                    for(int i = 0; i<result.length; i++){
//                        result[i] = result[i]/100;
//                    }

                    StringBuilder builder = new StringBuilder();
                    for (int i : result) {
                        builder.append(String.format("Sensor %d: ", idx++));
                        builder.append(i);
                        builder.append("\n");
                    }
                    scratchText.setText(builder.toString());
                    plotTemperatures(result);
                    storeTemperatures(result);

                    Log.d(TAG, "Scratch data received: " + builder.toString());
                    break;
                case BeanService.BLE_CONNECTED:
                    String name = intent.getStringExtra("Data");
                    beanName.setText(name);
                    break;
                case BeanService.BLE_DISCONNECTED:
                    beanName.setText("Not connected");
                    scratchText.setText("");
                    break;
            }
        }
    };

    private void storeTemperatures(int[] temp){

        // Write to file
        if (store_data){
            double[] timestamps = new double[1];
            timestamps[0] = (double)System.currentTimeMillis();

            Message msg = storageHandler.obtainMessage();
            msg.what = SimpleStorageWorker.MSG_WRITE;
            Bundle bundle = new Bundle();
            bundle.putInt("num_sample", 1);
            bundle.putInt("num_channel", temp.length);
            bundle.putDoubleArray("Time", timestamps);
            bundle.putIntArray("Data", temp);
            msg.setData(bundle);
            msg.sendToTarget();
        }
    }

    private void plotTemperatures(int[] temp){
        if (numberOfLines < temp.length) {
            for (int i = numberOfLines; i < temp.length; i++) {
                temperatures.add(new CircularArray<Integer>(numberOfPoints));
            }
            numberOfLines = temp.length;
        }

        List<Line> lines = new ArrayList<>();
        Iterator<CircularArray<Integer>> iterator = temperatures.iterator();

        for(int i = 0; i<temp.length; i++){
            CircularArray<Integer> temp_array = iterator.next();
            if (temp_array.size() > numberOfPoints)
                temp_array.popFirst();

            temp_array.addLast(temp[i]);

            List<PointValue> values = new ArrayList<>();
            for(int n = 0; n<temp_array.size(); n++){
                values.add(new PointValue(n, temp_array.get(n)));
            }
            Line line = new Line(values);
            line.setColor(ChartUtils.COLORS[i]);
            line.setHasLines(true);
            line.setHasPoints(false);
            lines.add(line);
        }
        data = new LineChartData(lines);
        data.setBaseValue(Float.NEGATIVE_INFINITY);
        chart.setLineChartData(data);
    }

    private void openFile(){
        Message msg = storageHandler.obtainMessage(SimpleStorageWorker.MSG_OPEN);
        Bundle b = new Bundle();
        b.putString("Prefix", beanName.getText().toString());
        msg.setData(b);
        msg.sendToTarget();
    }

    private void closeFile(){
        storageHandler.obtainMessage(SimpleStorageWorker.MSG_CLOSE).sendToTarget();
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.v(TAG, "BeanService connected");
            BeanService.LocalBinder binder = (BeanService.LocalBinder) service;
            beanService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.v(TAG, "BeanService disconnected");
            mBound = false;
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_storage) {
            if(!store_data) {
                openFile();
                store_data = true;
                item.setTitle(R.string.action_storage_disable);
            }
            else{
                closeFile();
                store_data = false;
                item.setTitle(R.string.action_storage_enable);
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
