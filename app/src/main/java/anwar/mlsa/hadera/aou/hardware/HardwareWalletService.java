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
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

public class HardwareWalletService extends Service implements SerialInputOutputManager.Listener {

    private static final String TAG = "HardwareWalletService";
    private static final String ACTION_USB_PERMISSION = "anwar.mlsa.hadera.aou.USB_PERMISSION";
    private static final int LEDGER_VID = 0x2C97; // Real Ledger Vendor ID
    private static final int TIMEOUT_MS = 30000;

    // APDU constants for Hedera Ledger App
    private static final byte CLA = (byte) 0xE0;
    private static final byte INS_GET_PUBKEY = 0x02;
    private static final byte INS_SIGN_TX = 0x04;
    private static final int SW_OK = 0x9000;

    public enum ConnectionStatus {DISCONNECTED, SEARCHING, CONNECTED, ERROR}
    private enum PendingOperation {NONE, GET_ACCOUNT, SIGN_TRANSACTION}

    private final IBinder binder = new LocalBinder();
    private UsbManager usbManager;
    private UsbSerialPort usbSerialPort;
    private SerialInputOutputManager serialInputOutputManager;

    private HardwareWalletListener signingListener;
    private AccountInfoListener accountInfoListener;
    private PendingOperation currentOperation = PendingOperation.NONE;
    private int activeAccountIndex = -1;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable operationTimeoutRunnable;

    private final MutableLiveData<ConnectionStatus> _connectionStatus = new MutableLiveData<>(ConnectionStatus.DISCONNECTED);
    public final LiveData<ConnectionStatus> connectionStatus = _connectionStatus;

    public interface HardwareWalletListener {
        void onSignatureReceived(byte[] signature);
        void onSignatureError(Exception e);
    }

    public interface AccountInfoListener {
        void onAccountInfoReceived(int accountIndex, byte[] publicKey);
        void onAccountInfoError(int accountIndex, Exception e);
    }

    public class LocalBinder extends Binder {
        public HardwareWalletService getService() {
            return HardwareWalletService.this;
        }
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) connectToDevice(device);
                    } else {
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
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
        unregisterReceiver(usbReceiver);
    }

    public void findAndConnectToDevice() {
        if (_connectionStatus.getValue() == ConnectionStatus.CONNECTED || _connectionStatus.getValue() == ConnectionStatus.SEARCHING) return;
        _connectionStatus.setValue(ConnectionStatus.SEARCHING);
        
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (availableDrivers.isEmpty()) {
            _connectionStatus.postValue(ConnectionStatus.DISCONNECTED);
            return;
        }
        for (UsbSerialDriver driver : availableDrivers) {
            UsbDevice device = driver.getDevice();
            // Checking both Ledger VID and common USB-Serial VIDs if testing with bridge
            if (device.getVendorId() == LEDGER_VID || device.getVendorId() == 11415) {
                if (usbManager.hasPermission(device)) {
                    connectToDevice(device);
                } else {
                    PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
                    usbManager.requestPermission(device, pi);
                }
                return;
            }
        }
        _connectionStatus.postValue(ConnectionStatus.DISCONNECTED);
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
            Log.e(TAG, "Connection failed", e);
            disconnect();
        }
    }

    public void disconnect() {
        cancelOperationTimeout();
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
        clearListeners();
        _connectionStatus.postValue(ConnectionStatus.DISCONNECTED);
    }

    public void signTransaction(byte[] unsignedTransaction, HardwareWalletListener listener) {
        if (_connectionStatus.getValue() != ConnectionStatus.CONNECTED) {
            listener.onSignatureError(new IOException("Not connected"));
            return;
        }
        this.signingListener = listener;
        this.currentOperation = PendingOperation.SIGN_TRANSACTION;
        try {
            // Hedera transactions can be large, we might need chunking for complex ones
            byte[] apdu = createSignTransactionApdu(unsignedTransaction);
            usbSerialPort.write(apdu, TIMEOUT_MS);
            startOperationTimeout();
        } catch (IOException e) {
            listener.onSignatureError(e);
        }
    }

    public void requestAccountInfo(int accountIndex, AccountInfoListener listener) {
        if (_connectionStatus.getValue() != ConnectionStatus.CONNECTED) {
            listener.onAccountInfoError(accountIndex, new IOException("Not connected"));
            return;
        }
        this.accountInfoListener = listener;
        this.currentOperation = PendingOperation.GET_ACCOUNT;
        this.activeAccountIndex = accountIndex;
        try {
            byte[] apdu = createGetPublicKeyApdu(accountIndex);
            usbSerialPort.write(apdu, TIMEOUT_MS);
            startOperationTimeout();
        } catch (IOException e) {
            listener.onAccountInfoError(accountIndex, e);
        }
    }

    private byte[] createBip32Path(int accountIndex) {
        // Path: 44'/3030'/accountIndex'/0/0
        ByteBuffer bb = ByteBuffer.allocate(20);
        bb.putInt(0x8000002C); // 44'
        bb.putInt(0x80000BD6); // 3030'
        bb.putInt(0x80000000 | accountIndex); // accountIndex'
        bb.putInt(0); // 0
        bb.putInt(0); // 0
        return bb.array();
    }

    private byte[] createGetPublicKeyApdu(int accountIndex) {
        byte[] path = createBip32Path(accountIndex);
        byte[] header = {CLA, INS_GET_PUBKEY, 0x00, 0x01, (byte) path.length}; // P2=0x01 to display on screen
        byte[] apdu = new byte[header.length + path.length];
        System.arraycopy(header, 0, apdu, 0, header.length);
        System.arraycopy(path, 0, apdu, header.length, path.length);
        return apdu;
    }

    private byte[] createSignTransactionApdu(byte[] transaction) {
        // Hedera Ledger app expects: Path (20 bytes) + Transaction Body
        byte[] path = createBip32Path(activeAccountIndex != -1 ? activeAccountIndex : 0);
        byte[] header = {CLA, INS_SIGN_TX, 0x00, 0x00, (byte) (path.length + transaction.length)};
        byte[] apdu = new byte[header.length + path.length + transaction.length];
        System.arraycopy(header, 0, apdu, 0, header.length);
        System.arraycopy(path, 0, apdu, header.length, path.length);
        System.arraycopy(transaction, 0, apdu, header.length + path.length, transaction.length);
        return apdu;
    }

    @Override
    public void onNewData(byte[] data) {
        cancelOperationTimeout();
        if (data.length < 2) return;

        // Last 2 bytes are Status Word (SW)
        int sw = ((data[data.length - 2] & 0xFF) << 8) | (data[data.length - 1] & 0xFF);
        byte[] responseData = Arrays.copyOfRange(data, 0, data.length - 2);

        mainHandler.post(() -> {
            if (sw != SW_OK) {
                handleError(new Exception("Ledger Error: " + String.format("0x%04X", sw)));
                return;
            }

            try {
                switch (currentOperation) {
                    case SIGN_TRANSACTION:
                        if (signingListener != null) signingListener.onSignatureReceived(responseData);
                        break;
                    case GET_ACCOUNT:
                        if (accountInfoListener != null) {
                            accountInfoListener.onAccountInfoReceived(activeAccountIndex, responseData);
                        }
                        break;
                }
            } finally {
                clearListeners();
            }
        });
    }

    private void startOperationTimeout() {
        cancelOperationTimeout();
        operationTimeoutRunnable = () -> handleError(new TimeoutException("Ledger operation timed out"));
        mainHandler.postDelayed(operationTimeoutRunnable, TIMEOUT_MS);
    }

    private void cancelOperationTimeout() {
        if (operationTimeoutRunnable != null) {
            mainHandler.removeCallbacks(operationTimeoutRunnable);
            operationTimeoutRunnable = null;
        }
    }

    private void clearListeners() {
        signingListener = null;
        accountInfoListener = null;
        currentOperation = PendingOperation.NONE;
    }

    @Override
    public void onRunError(Exception e) {
        mainHandler.post(() -> handleError(e));
    }

    private void handleError(Exception e) {
        cancelOperationTimeout();
        if (currentOperation == PendingOperation.SIGN_TRANSACTION && signingListener != null) {
            signingListener.onSignatureError(e);
        } else if (currentOperation == PendingOperation.GET_ACCOUNT && accountInfoListener != null) {
            accountInfoListener.onAccountInfoError(activeAccountIndex, e);
        }
        clearListeners();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
