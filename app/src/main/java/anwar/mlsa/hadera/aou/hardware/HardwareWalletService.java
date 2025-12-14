package anwar.mlsa.hadera.aou.hardware;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

public class HardwareWalletService extends Service implements SerialInputOutputManager.Listener {

    private static final String TAG = "HardwareWalletService";
    private static final String ACTION_USB_PERMISSION = "anwar.mlsa.hadera.aou.USB_PERMISSION";
    private static final int LEDGER_VID = 11415; // Vendor ID for Ledger
    private static final int SIGNING_TIMEOUT_MS = 20000; // 20 seconds

    public enum ConnectionStatus {DISCONNECTED, SEARCHING, CONNECTED, ERROR}

    private final IBinder binder = new LocalBinder();
    private UsbManager usbManager;
    private UsbSerialPort usbSerialPort;
    private SerialInputOutputManager serialInputOutputManager;
    private HardwareWalletListener signingListener;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable signingTimeoutRunnable;

    private final MutableLiveData<ConnectionStatus> _connectionStatus = new MutableLiveData<>(ConnectionStatus.DISCONNECTED);
    public final LiveData<ConnectionStatus> connectionStatus = _connectionStatus;

    public interface HardwareWalletListener {
        void onSignatureReceived(byte[] signature);
        void onSignatureError(Exception e);
    }

    public class LocalBinder extends Binder {
        public HardwareWalletService getService() {
            return HardwareWalletService.this;
        }
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            Log.d(TAG, "Permission granted for device " + device.getDeviceName());
                            connectToDevice(device);
                        }
                    } else {
                        Log.d(TAG, "Permission denied for device " + device.getDeviceName());
                        _connectionStatus.postValue(ConnectionStatus.DISCONNECTED);
                    }
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_PERMISSION));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
        unregisterReceiver(usbReceiver);
    }

    public void findAndConnectToDevice() {
        if (_connectionStatus.getValue() == ConnectionStatus.CONNECTED || _connectionStatus.getValue() == ConnectionStatus.SEARCHING) {
            return;
        }
        
        _connectionStatus.setValue(ConnectionStatus.SEARCHING);

        // Post a small delay to allow the UI to update before starting the heavy work.
        mainHandler.postDelayed(() -> {
            List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
            if (availableDrivers.isEmpty()) {
                _connectionStatus.postValue(ConnectionStatus.DISCONNECTED);
                return;
            }
            for (UsbSerialDriver driver : availableDrivers) {
                UsbDevice device = driver.getDevice();
                if (device.getVendorId() == LEDGER_VID) {
                    if (usbManager.hasPermission(device)) {
                        connectToDevice(device);
                    } else {
                        PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
                        usbManager.requestPermission(device, permissionIntent);
                    }
                    return; 
                }
            }
            _connectionStatus.postValue(ConnectionStatus.DISCONNECTED);
        }, 100); 
    }

    private void connectToDevice(UsbDevice device) {
        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            _connectionStatus.postValue(ConnectionStatus.ERROR);
            return;
        }
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if (driver == null || driver.getPorts().isEmpty()) {
            _connectionStatus.postValue(ConnectionStatus.ERROR);
            return;
        }
        usbSerialPort = driver.getPorts().get(0);
        try {
            usbSerialPort.open(connection);
            usbSerialPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            serialInputOutputManager = new SerialInputOutputManager(usbSerialPort, this);
            Executors.newSingleThreadExecutor().submit(serialInputOutputManager);
            _connectionStatus.postValue(ConnectionStatus.CONNECTED);
        } catch (IOException e) {
            disconnect();
        }
    }

    public void disconnect() {
        cancelSigningTimeout();
        if (serialInputOutputManager != null) {
            serialInputOutputManager.stop();
            serialInputOutputManager = null;
        }
        if (usbSerialPort != null) {
            try {
                usbSerialPort.close();
            } catch (IOException ignored) {}
            usbSerialPort = null;
        }
        _connectionStatus.postValue(ConnectionStatus.DISCONNECTED);
    }

    public void signTransaction(byte[] unsignedTransaction, HardwareWalletListener listener) {
        if (_connectionStatus.getValue() != ConnectionStatus.CONNECTED || usbSerialPort == null) {
            listener.onSignatureError(new IOException("Not connected to hardware wallet."));
            return;
        }
        this.signingListener = listener;

        try {
            byte[] apduCommand = createSigningApdu(unsignedTransaction);
            usbSerialPort.write(apduCommand, 2000);
            startSigningTimeout();
        } catch (IOException e) {
            listener.onSignatureError(e);
        }
    }

    private void startSigningTimeout() {
        cancelSigningTimeout();
        signingTimeoutRunnable = () -> {
            if (signingListener != null) {
                signingListener.onSignatureError(new TimeoutException("Hardware wallet signing timed out."));
                signingListener = null;
            }
        };
        mainHandler.postDelayed(signingTimeoutRunnable, SIGNING_TIMEOUT_MS);
    }

    private void cancelSigningTimeout() {
        if (signingTimeoutRunnable != null) {
            mainHandler.removeCallbacks(signingTimeoutRunnable);
            signingTimeoutRunnable = null;
        }
    }

    private byte[] createSigningApdu(byte[] transaction) {
        byte cla = (byte) 0xE0;
        byte ins = 0x04;
        byte p1 = 0x00;
        byte p2 = 0x00;
        byte lc = (byte) transaction.length;
        byte[] header = {cla, ins, p1, p2, lc};
        byte[] apdu = new byte[header.length + transaction.length];
        System.arraycopy(header, 0, apdu, 0, header.length);
        System.arraycopy(transaction, 0, apdu, header.length, transaction.length);
        return apdu;
    }

    @Override
    public void onNewData(byte[] data) {
        cancelSigningTimeout();
        if (signingListener != null) {
            signingListener.onSignatureReceived(data);
            signingListener = null;
        }
    }

    @Override
    public void onRunError(Exception e) {
        cancelSigningTimeout();
        if (signingListener != null) {
            signingListener.onSignatureError(e);
            signingListener = null;
        }
        disconnect();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
