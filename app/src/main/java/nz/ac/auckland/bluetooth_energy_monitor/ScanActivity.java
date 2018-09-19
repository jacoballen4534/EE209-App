package nz.ac.auckland.bluetooth_energy_monitor;

import android.Manifest;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ScanActivity extends AppCompatActivity { //Use to be AppCompatActivity
    private BluetoothAdapter mBluetoothAdapter;
    private static final int REQUEST_ENABLE_BT = 1;
    private static int REQUEST_LOCATION = 2;
    private BluetoothLeScanner BLE_Scanner;
    private Handler mHandler;
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private static final long SCAN_PERIOD = 6000;
    private ProgressBar progressBar;
    private int progress = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        setTitle("My new Title");
//        getActionBar().setIcon(R.drawable.);
        mHandler = new Handler();


        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        mBluetoothAdapter = ((BluetoothManager) Objects.requireNonNull(getSystemService(Context.BLUETOOTH_SERVICE))).getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            RequestLocationPopup("This application requires a Bluetooth compatible device",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    });
        }


        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            RequestLocationPopup("This application requires a BLE compatible device",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    });
        }

        // Check if location is on, if it is not on, request.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            //First time, so just ask for permission
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 2);
        }


        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 24));

        final FrameLayout decorView = (FrameLayout) getWindow().getDecorView();
        decorView.addView(progressBar);

        ViewTreeObserver observer = progressBar.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                View contentView = decorView.findViewById(android.R.id.content);
                progressBar.setY(contentView.getY() + 30);

                ViewTreeObserver observer = progressBar.getViewTreeObserver();
                observer.removeOnGlobalLayoutListener(this);
            }
        });


        ((ListView) findViewById(R.id.listView)).setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
                if (device == null) return;
                final Intent intent = new Intent(findViewById(R.id.listView).getContext(), DeviceControlActivity.class);
                intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
                intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
                startActivity(intent);
            }
        });
    }

    public void ScanButtonClicked(View v) {
        if (RecheckRequirements()) {
            findViewById(R.id.welcomeText).setVisibility(View.GONE);
            findViewById(R.id.scanDescriptionText).setVisibility(View.GONE);
            findViewById(R.id.PermissionDenied).setVisibility(View.GONE);
            findViewById(R.id.listView).setVisibility(View.VISIBLE);
            findViewById(R.id.scanButton).setEnabled(false);
            scanLeDevice();

        } else {
            findViewById(R.id.welcomeText).setVisibility(View.INVISIBLE);
            findViewById(R.id.scanDescriptionText).setVisibility(View.INVISIBLE);
            findViewById(R.id.PermissionDenied).setVisibility(View.VISIBLE);
            findViewById(R.id.listView).setVisibility(View.INVISIBLE);
        }
    }


    private boolean RecheckRequirements() {
        //If bluetooth is disabled, request it.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return false;
        }

        // If location permission is denied, request it.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            REQUEST_LOCATION = 2;
            //Check what type of request to display.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                //Give explanation

                RequestLocationPopup("This app does not use your location.\n" +
                                "However it is required to use bluetooth,\n" +
                                "so please allow access to continue",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                        REQUEST_LOCATION);
                            }
                        });
            } else {
                //Tell the user that the request is required
                RequestLocationPopup("OK will redirect you to Settings.\n\n" +
                                "Please navigate to permissions and allow location access.\n\n" +
                                "This app does not use your location, " +
                                "however, it is required in order to use bluetooth.",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //Open settings page to allow permissions.
                                Intent openSettings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + BuildConfig.APPLICATION_ID));
                                startActivity(openSettings);
                            }
                        });
            }
            return false;
        }
        return true;
    }


    private void RequestLocationPopup(String message, DialogInterface.OnClickListener acceptListner) {
        new AlertDialog.Builder(ScanActivity.this)
                .setMessage(message)
                .setPositiveButton("Ok", acceptListner)
                .create()
                .show();
    }


    private void scanLeDevice() {
        // Initializes list view adapter.
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        ListView listView = findViewById(R.id.listView);
        listView.setAdapter(mLeDeviceListAdapter);
        BLE_Scanner = mBluetoothAdapter.getBluetoothLeScanner();

        BLE_Scanner.startScan(null, new ScanSettings.Builder().build(), mLeScanCallback);
        setTitle("Scanning...");
        invalidateOptionsMenu();
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (progress < SCAN_PERIOD) {
                    progress += 10;

                    try{
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setProgress((int)(progress * 100/SCAN_PERIOD), true);
                        }
                    });
                }
                progress = 0;
                progressBar.setProgress(0, true);

            }
        }).start();


        // Stops scanning after a pre-defined scan period.
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                BLE_Scanner.stopScan(mLeScanCallback);
                findViewById(R.id.scanButton).setEnabled(true);
                setTitle("Nearby Devices");
                invalidateOptionsMenu();
                if (mLeDeviceListAdapter.isEmpty()){
                    setTitle("No Nearby Devices Found");
                }
            }
        }, SCAN_PERIOD);
    }

    // Device scan object callback.
    private ScanCallback mLeScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, final ScanResult result) {
                    //super.onScanResult(callbackType, result);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            mLeDeviceListAdapter.addDevice(result.getDevice());
                            mLeDeviceListAdapter.notifyDataSetChanged();
                        }
                    });
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    super.onBatchScanResults(results);
                }

                @Override
                public void onScanFailed(int errorCode) {
                    super.onScanFailed(errorCode);
                }
            };

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflater;

        LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mInflater = ScanActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if(!mLeDevices.contains(device) && device.getName() != null) { // Only desplay devices with name
                mLeDevices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflater.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = view.findViewById(R.id.device_address);
                viewHolder.deviceName = view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0) {
                viewHolder.deviceName.setText(deviceName);
            } else {
                viewHolder.deviceName.setText(R.string.unknown_device);
            }
            viewHolder.deviceAddress.setText(device.getAddress());
            return view;
        }
    }

}