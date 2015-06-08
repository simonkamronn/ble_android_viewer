package care.siren.beanviewer;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import nl.littlerobots.bean.Bean;
import nl.littlerobots.bean.BeanDiscoveryListener;
import nl.littlerobots.bean.BeanListener;
import nl.littlerobots.bean.BeanManager;

public class BeanService extends Service {
    private static final String TAG = BeanService.class.getSimpleName();
    private final IBinder binder = new LocalBinder();

    public static final String SCRATCH_RECV = "Scratch received";

    Bean mBean;
    BeanListener beanListener;
    BeanDiscoveryListener beanDiscoveryListener;
    String serialMsg = "";

    @Override
    public void onCreate() {
        super.onCreate();
        beanDiscoveryListener = new BeanDiscoveryListener() {

            @Override
            public void onBeanDiscovered(Bean bean) {
                Log.d(TAG, "Discovered: " + bean.getDevice().getName());
                if (bean.getDevice().getName().startsWith("SIREN")) {
                    Log.i(TAG, "Connecting to Bean");
                    mBean = bean;
                    mBean.connect(getApplicationContext(), beanListener);
                }
            }

            @Override
            public void onDiscoveryComplete(){

            }
        };

        beanListener = new BeanListener() {
            @Override
            public void onConnected() {
                Log.i(TAG, "Bean connected");
            }

            @Override
            public void onConnectionFailed() {
                Log.i(TAG, "Bean connection failed");

            }

            @Override
            public void onDisconnected() {
                Log.i(TAG, "Bean disconnected");

            }

            @Override
            public void onSerialMessageReceived(byte[] bytes) {
                String output = new String(bytes, 0, bytes.length); // Add read buffer to new string
                serialMsg += output;
                if (serialMsg.endsWith("\n")){
                    Log.i(TAG, "Serial message: " + serialMsg);
                    serialMsg = "";
                }
            }

            @Override
            public void onScratchValueChanged(int i, byte[] bytes) {
                int[] result = new int[bytes.length/2];
                for (int n = 0; n < bytes.length/2; n++) {
                     result[n] = (bytes[n*2] & 0xff) |
                                ((bytes[n*2+1] & 0xff) << 8);
                }

                // Send data to activity
                sendMessage(SCRATCH_RECV, result);
            }
        };
        BeanManager.getInstance().startDiscovery(beanDiscoveryListener);
    }

    // Send an Intent with the data
    private void sendMessage(String action, int[] data) {
        Intent intent = new Intent(action);
        intent.putExtra("Data", data);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "BeanService started");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBean != null) {
            if (mBean.isConnected())
                mBean.disconnect();
        }
    }

    public BeanService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class LocalBinder extends Binder {
        BeanService getService(){
            return BeanService.this;
        }
    }
}
