package com.example.webappsampler;

import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.device.ConnectableDeviceListener;
import com.connectsdk.device.DevicePicker;
import com.connectsdk.discovery.CapabilityFilter;
import com.connectsdk.discovery.DiscoveryManager;
import com.connectsdk.service.DeviceService;
import com.connectsdk.service.DeviceService.PairingType;
import com.connectsdk.service.capability.WebAppLauncher;
import com.connectsdk.service.capability.listeners.ResponseListener;
import com.connectsdk.service.command.ServiceCommandError;
import com.connectsdk.service.sessions.WebAppSession;

public class FullscreenActivity extends Activity {
	private Button mLaunchButton;
	private Button mCloseButton;
	private Button mSendButton;
	
	private EditText mMessageText;
	private TextView mStatusTextView;
	
	private DevicePicker mDevicePicker;
	
	private ConnectableDevice mDevice;
	private WebAppSession mWebAppSession;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_fullscreen);
		
		mLaunchButton = (Button) findViewById(R.id.launchButton);
		mLaunchButton.setOnClickListener(mLaunchClickListener);
		
		mCloseButton = (Button) findViewById(R.id.closeButton);
		mCloseButton.setOnClickListener(mCloseClickListener);
		
		mSendButton = (Button) findViewById(R.id.sendButton);
		mSendButton.setOnClickListener(sendClickListener);
		
		mMessageText = (EditText) findViewById(R.id.messageText);
		mStatusTextView = (TextView) findViewById(R.id.statusTextView);
		
		DiscoveryManager.init(getApplicationContext());
		
		CapabilityFilter webAppFilter = new CapabilityFilter(WebAppLauncher.Launch, WebAppLauncher.Close, WebAppLauncher.Message_Send);
		
		DiscoveryManager.getInstance().setCapabilityFilters(webAppFilter);
		DiscoveryManager.getInstance().start();
		
		mDevicePicker = new DevicePicker(this);
	}
	
	private void handleConnectSuccess() {
		mLaunchButton.setEnabled(false);
		mCloseButton.setEnabled(true);
		mSendButton.setEnabled(true);
		mMessageText.setEnabled(true);
	}
	
	private void cleanup() {
		if (mWebAppSession != null)
		{
			mWebAppSession.disconnectFromWebApp();
			mWebAppSession = null;
		}
		
		if (mDevice != null)
		{
			mDevice.removeListener(mDeviceListener);
			mDevice.disconnect();
			mDevice = null;
		}
		
		mLaunchButton.setEnabled(true);
		mCloseButton.setEnabled(false);
		mSendButton.setEnabled(false);
		mMessageText.setEnabled(false);
	}
	
	private void log(String message) {
		String currentMessage = mStatusTextView.getText().toString();
		String newMessage = message + "\n" + currentMessage;
		mStatusTextView.setText(newMessage);
	}
	
	private View.OnClickListener mLaunchClickListener = new View.OnClickListener() {
		
		@Override
		public void onClick(View v) {
			if (mWebAppSession != null || mDevice != null)
				cleanup();
			
			AlertDialog alertDialog = mDevicePicker.getPickerDialog("Select a device", mDeviceSelectListener);
			alertDialog.show();
		}
	};
	
	private View.OnClickListener mCloseClickListener = new View.OnClickListener() {
		
		@Override
		public void onClick(View v) {
			log("Closing web app");
			
			mWebAppSession.close(null);
			mWebAppSession = null;
			cleanup();
		}
	};
	
	private View.OnClickListener sendClickListener = new View.OnClickListener() {
		
		@Override
		public void onClick(View v) {
			final String message = mMessageText.getText().toString();
			
			log("Sending message: \"" + message + "\"");
			
			mWebAppSession.sendMessage(message, new ResponseListener<Object>() {
				
				@Override
				public void onError(ServiceCommandError error) {
					log("Could not send message, disconnecting...");
					cleanup();
				}
				
				@Override
				public void onSuccess(Object object) {
					log("Message \"" + message + "\" sent successfully.");
				}
			});
			
			mMessageText.setText("");
		}
	};
	
	private AdapterView.OnItemClickListener mDeviceSelectListener = new AdapterView.OnItemClickListener() {
		public void onItemClick(android.widget.AdapterView<?> arg0, View arg1, int arg2, long arg3) {
			mStatusTextView.setText("");
			
			mDevice = (ConnectableDevice)arg0.getItemAtPosition(arg2);
			log("Connecting to device "  + mDevice.getFriendlyName());
			mDevice.addListener(mDeviceListener);
			mDevice.connect();
		};
	};
	
	private ConnectableDeviceListener mDeviceListener = new ConnectableDeviceListener() {
		
		@Override
		public void onPairingRequired(ConnectableDevice device,
				DeviceService service, PairingType pairingType) {
			// since we haven't enabled pairing, we don't need to solve for this case
		}
		
		@Override
		public void onDeviceReady(ConnectableDevice device) {
			log("Connected to device "  + device.getFriendlyName());
			
			String webAppId = null;
			
			if (device.getServiceByName("Chromecast")!= null)
				webAppId = "DDCEDE96";
			else if (device.getServiceByName("webOS TV") != null)
				webAppId = "SampleWebApp";
			
			if (webAppId != null)
			{
				log("Launching web app with id " + webAppId);
				device.getWebAppLauncher().launchWebApp(webAppId, mWebAppLaunchListener);
			}
		}
		
		@Override
		public void onDeviceDisconnected(ConnectableDevice device) {
			log("Disconnected from device");
			cleanup();
		}
		
		@Override
		public void onConnectionFailed(ConnectableDevice device,
				ServiceCommandError error) {
			log("Could not connect to device " + error.getLocalizedMessage());
			cleanup();
		}
		
		@Override
		public void onCapabilityUpdated(ConnectableDevice device,
				List<String> added, List<String> removed) {
			// we can ignore this case
		}
	};
	
	private WebAppSession.LaunchListener mWebAppLaunchListener = new WebAppSession.LaunchListener() {
		
		@Override
		public void onError(ServiceCommandError error) {
			log("Web app could not be launched: " + error.getLocalizedMessage());
			cleanup();
		}
		
		@Override
		public void onSuccess(WebAppSession webAppSession) {
			log("Web app launch successful, connecting... ");
			
			mWebAppSession = webAppSession;
			mWebAppSession.connect(mWebAppConnectListener);
		}
	};
	
	private ResponseListener<Object> mWebAppConnectListener = new ResponseListener<Object>() {
		public void onSuccess(Object object) {
			log("Web app connected!");
			handleConnectSuccess();
		};
		
		@Override
		public void onError(ServiceCommandError error) {
			log("Web app could not be connected: " + error.getLocalizedMessage());
			cleanup();
		}
	};
}
