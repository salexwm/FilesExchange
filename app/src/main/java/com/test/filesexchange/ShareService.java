package com.test.filesexchange;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.SimpleArrayMap;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;


public class ShareService extends Service implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "ShareService";
    private LocalBinder mLocalBinder = new LocalBinder();

    private static final String SERVICE_ID = "com.test.filesexchange.SERVICE_ID";


    public static final int STATE_CONNECTED = 600;


    public static final int STATE_READY_SEND_DATA = 650;

    /**
     * The connection strategy
     * P2P_STAR, which is a combination of Bluetooth Classic and WiFi Hotspots.
     */
    private static final Strategy STRATEGY = Strategy.P2P_STAR;

    private GoogleApiClient mGoogleApiClient;

    /** The devices discovered near */
    private final Map<String, Endpoint> mDiscoveredEndpoints = new HashMap<>();
    /** The devices not yet connected, intermediate state */
    private final Map<String, Endpoint> mPendingConnections = new HashMap<>();

    /** Payload.getId(), payload */
    private final SimpleArrayMap<Long, Payload> incomingFilePayloads = new SimpleArrayMap<>();

    /** Payload.getId(), payload */
    private final SimpleArrayMap<Long, Payload> outgoingPayloads = new SimpleArrayMap<>();

    /** payloadId, filename */
    private final SimpleArrayMap<String, String> filePayloadFilenames = new SimpleArrayMap<>();

    /**
     * The devices currently connected to. For advertisers, this may be large. For discoverers,
     * there will only be one entry in this map.
     * endpoint.getId, Endpoint
     */
    private final Map<String, Endpoint> mEstablishedConnections = new HashMap<>();

    /**
     * True if we are asking a discovered device to connect to us.
     * While we ask, we cannot ask another device.
     */
    private boolean mIsConnecting = false;

    /** True if we are discovering. */
    private boolean mIsDiscovering = false;

    /** True if we are advertising. */
    private boolean mIsAdvertising = false;

    private Payload filePayload;
    private Endpoint endpnt;
    private Uri uri;
    private ServiceListener listener;

    public void setListener(ServiceListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate() {

        super.onCreate();
        Log.d(TAG, "onCreate");

        createGoogleApiClient();
        mGoogleApiClient.connect();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mLocalBinder;
    }

    private void createGoogleApiClient() {
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Nearby.CONNECTIONS_API)
                    .addConnectionCallbacks(this)
                    .build();
        }
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "GoogleApiClient->Connected");
        listener.onStateUpdate(STATE_CONNECTED);
        disconnectFromAllEndpoints();
//        startDiscovering();
//        startAdvertising();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "GoogleApiClient->onConnectionSuspended " + i);

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "GoogleApiClient->onConnectionFailed");

    }


    public class LocalBinder extends Binder {
        public ShareService getService() {
            return ShareService.this;
        }
    }

    public void sendFile(Uri fileUri) {

        if (mEstablishedConnections.values().size() > 0) {
            for (Endpoint endpoint : mEstablishedConnections.values()) {
                try {
                    // Open the ParcelFileDescriptor for this URI with read access.
                    ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(fileUri, "r");

                    filePayload = Payload.fromFile(pfd);
                    endpnt = endpoint;

                    // Construct a simple message mapping the ID of the file payload to the desired filename.
                    String payloadFilenameMessage = filePayload.getId() + ":" + fileUri.getLastPathSegment();
                    Log.d(TAG, "sendFile: filename message " + payloadFilenameMessage);


                    // Send this message as a bytes payload.
                    Payload payload = Payload.fromBytes(payloadFilenameMessage.getBytes("UTF-8"));
                    outgoingPayloads.put(payload.getId(), payload);

                    Nearby.Connections.sendPayload(mGoogleApiClient,
                            endpoint.getId(), payload);


//                    // Finally, send the file payload.
//                    Nearby.Connections.sendPayload(mGoogleApiClient,
//                            endpoint.getId(),
//                            filePayload);


                } catch (FileNotFoundException | UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        } else {
            Log.e(TAG, "sendFile: EstablishedConnections == 0");
        }
    }

    protected void disconnectFromAllEndpoints() {
        Log.d(TAG, "disconnectFromAllEndpoints");
        for (Endpoint endpoint : mEstablishedConnections.values()) {
            Nearby.Connections.disconnectFromEndpoint(mGoogleApiClient, endpoint.getId());
        }
        mEstablishedConnections.clear();
    }



    // ======= <Discovering> =========
    public void startDiscovering() {
        Log.d(TAG, "startDiscovering");

        mIsDiscovering = true;
        mDiscoveredEndpoints.clear();

        Nearby.Connections.startDiscovery(
                mGoogleApiClient,
                SERVICE_ID,
                new EndpointDiscoveryCallback() {
                    @Override
                    public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                        Log.d(TAG, String.format("onEndpointFound(endpointId=%s, serviceId=%s, endpointName=%s)",
                                endpointId, info.getServiceId(), info.getEndpointName()));

                        if (SERVICE_ID.equals(info.getServiceId())) {
                            Endpoint endpoint = new Endpoint(endpointId, info.getEndpointName());
                            mDiscoveredEndpoints.put(endpointId, endpoint);
                            onEndpointDiscovered(endpoint);
                        }
                    }

                    @Override
                    public void onEndpointLost(String endpointId) {
                        Log.d(TAG, String.format("onEndpointLost(endpointId=%s)", endpointId));
                    }
                },
                new DiscoveryOptions(STRATEGY))
                .setResultCallback(status -> {
                            if (status.isSuccess()) {
                                onDiscoveryStarted();
                            } else {
                                mIsDiscovering = false;
                                Log.w(TAG, String.format("Discovering failed. Received status %d", status.getStatusCode()));
                                onDiscoveryFailed();
                            }
                        });
    }

    /** Stops discovery. */
    protected void stopDiscovering() {
        Log.d(TAG, "stopDiscovering");



        mIsDiscovering = false;
        Nearby.Connections.stopDiscovery(mGoogleApiClient);
    }

    protected void onDiscoveryStarted() {

    }

    protected void onDiscoveryFailed() {

    }

    protected void onEndpointDiscovered(Endpoint endpoint) {
        connectToEndpoint(endpoint);
    }

    protected void connectToEndpoint(final Endpoint endpoint) {
        // If we already sent out a connection request, wait for it to return
        // before we do anything else. P2P_STAR only allows 1 outgoing connection.
        if (mIsConnecting) {
            Log.w(TAG, "Already connecting, so ignoring this endpoint: " + endpoint);
            return;
        }

        Log.w(TAG, "Sending a connection request to endpoint " + endpoint);
        // Mark ourselves as connecting so we don't connect multiple times
        mIsConnecting = true;

        // Ask to connect
        Nearby.Connections.requestConnection(
                mGoogleApiClient, generateRandomName(), endpoint.getId(), mConnectionLifecycleCallback)
                .setResultCallback(status -> {
                    if (!status.isSuccess()) {
                        Log.w(TAG, String.format("requestConnection failed %s", status.getStatusMessage()));
                        mIsConnecting = false;
                        onConnectionFailed(endpoint);
                    }
                });
    }

    // ======= </Discovering> =========

    // ======= Advertising =========
    public void startAdvertising() {

        Log.d(TAG, "startAdvertising");

        mIsAdvertising = true;

        Nearby.Connections.startAdvertising(
                mGoogleApiClient,
                generateRandomName(),
                SERVICE_ID,
                mConnectionLifecycleCallback,
                new AdvertisingOptions(STRATEGY))
                .setResultCallback(
                        result -> {
                            if (result.getStatus().isSuccess()) {
                                Log.d(TAG, "Now advertising endpoint " + result.getLocalEndpointName());
                                onAdvertisingStarted();

                            } else {
                                mIsAdvertising = false;
                                Log.w(TAG, String.format("Advertising failed. Received status %d",
                                        result.getStatus().getStatusCode()));

                                onAdvertisingFailed();
                            }
                        });
    }

    protected void stopAdvertising() {
        Log.d(TAG, "stopAdvertising");
        mIsAdvertising = false;
        Nearby.Connections.stopAdvertising(mGoogleApiClient);
    }

    protected boolean isAdvertising() {
        return mIsAdvertising;
    }

    protected void onAdvertisingStarted() {

    }

    protected void onAdvertisingFailed() {

    }
    // ======= /Advertising =========

    private final ConnectionLifecycleCallback mConnectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
                    Log.d(TAG, String.format(
                                    "onConnectionInitiated(endpointId=%s, endpointName=%s)",
                                    endpointId, connectionInfo.getEndpointName()));

                    Endpoint endpoint = new Endpoint(endpointId, connectionInfo.getEndpointName());
                    mPendingConnections.put(endpointId, endpoint);
                    ShareService.this.onConnectionInitiated(endpoint, connectionInfo);
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {
                    Log.d(TAG, String.format("onConnectionResponse(endpointId=%s, result=%s)", endpointId, result));

                    // We're no longer connecting
                    mIsConnecting = false;

                    if (!result.getStatus().isSuccess()) {
                        Log.w(TAG, String.format("Connection failed. Received status"));
                        onConnectionFailed(mPendingConnections.remove(endpointId));
                        return;
                    }
                    connectedToEndpoint(mPendingConnections.remove(endpointId));
                }

                @Override
                public void onDisconnected(String endpointId) {
                    if (!mEstablishedConnections.containsKey(endpointId)) {
                        Log.w(TAG, "Unexpected disconnection from endpoint " + endpointId);
                        return;
                    }
                    disconnectedFromEndpoint(mEstablishedConnections.get(endpointId));
                }
            };


    protected void onConnectionFailed(Endpoint endpoint) {
        Log.d(TAG, "onConnectionFailed");
    }

    protected void onConnectionInitiated(Endpoint endpoint, ConnectionInfo connectionInfo) {
        // accept the connection immediately.
        acceptConnection(endpoint);
    }

    protected void acceptConnection(final Endpoint endpoint) {
        Nearby.Connections.acceptConnection(mGoogleApiClient,
                endpoint.getId(),
                mPayloadCallback)
                .setResultCallback(status -> {
                            if (!status.isSuccess()) {
                                Log.w(TAG, String.format("acceptConnection failed."));
                            }
                        });
    }

    /** Callbacks for payloads (bytes of data) sent from another device */
    private final PayloadCallback mPayloadCallback = new PayloadCallback() {
                /** Called when a Payload is received from a remote endpoint. */
                @Override
                public void onPayloadReceived(String endpointId, Payload payload) {
                    Log.d(TAG, String.format("onPayloadReceived(endpointId=%s, payload=%s)", endpointId, payload));

                    try {
                        if (payload.getType() == Payload.Type.BYTES) {
                            Log.d(TAG, "onPayloadReceived: Payload.Type.BYTES");
                            String payloadFilenameMessage = new String(payload.asBytes(), "UTF-8");
                            Log.d(TAG, "onPayloadReceived: BYTES " + payloadFilenameMessage);
                            addPayloadFilename(payloadFilenameMessage);
                        } else if (payload.getType() == Payload.Type.FILE) {
                            // Add this to our tracking map, so that we can retrieve the payload later.
                            incomingFilePayloads.put(payload.getId(), payload);

                            Log.d(TAG, "onPayloadReceived: Payload.Type.FILE");
                        } else if (payload.getType() == Payload.Type.STREAM) {
                            //payload.asStream().asInputStream()
                            Log.d(TAG, "onPayloadReceived: Payload.Type.STREAM");
                        }
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }

                /** Called with progress information about an active Payload transfer, either incoming or outgoing. */
                @Override
                public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
                    Log.d(TAG, String.format("onPayloadTransferUpdate(endpointId=%s, update=%s)",
                            endpointId, update));

                    switch(update.getStatus()) {

                        case PayloadTransferUpdate.Status.IN_PROGRESS:
                            long size = update.getTotalBytes();
                            if (size == -1) {
                                // This is a stream payload, so we don't need to update anything at this point.
                                return;
                            }

                            Log.d(TAG, "onPayloadTransferUpdate: IN_PROGRESS " + update.getBytesTransferred());

                            break;

                            case PayloadTransferUpdate.Status.SUCCESS:
                                Log.d(TAG, "onPayloadTransferUpdate: SUCCESS");
                                Payload payload = incomingFilePayloads.remove(update.getPayloadId());

                                // Sender byte payload
                                Payload bytePayload = outgoingPayloads.remove(update.getPayloadId());
                                if (bytePayload != null && bytePayload.getType() == Payload.Type.BYTES) {
                                    Log.d(TAG, "onPayloadTransferUpdate: bytePayload " + bytePayload.getId());
                                    if (endpnt != null && filePayload != null) {
                                        try {
                                            Thread.sleep(5000);
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                        Nearby.Connections.sendPayload(mGoogleApiClient,
                                                endpnt.getId(),
                                                filePayload);
                                    }
                                }


                                if (payload != null && payload.getType() == Payload.Type.FILE) {
                                    Log.d(TAG, "onPayloadTransferUpdate: FILE " + payload.getId());
                                    // Retrieve the filename that was received in a bytes payload.
                                    String newFilename = filePayloadFilenames.remove(update.getPayloadId());

                                    File payloadFile = payload.asFile().asJavaFile();
                                    // Rename the file.
                                    payloadFile.renameTo(new File(payloadFile.getParentFile(), newFilename));
                                }

                            break;
                        case PayloadTransferUpdate.Status.FAILURE:
                            Log.d(TAG, "onPayloadTransferUpdate: FAILURE");

                            break;
                    }


                }
            };

    private void addPayloadFilename(String payloadFilenameMessage) {
        try {
            // filePayload.getId() + ":" + uri.getLastPathSegment()
            int colonIndex = payloadFilenameMessage.indexOf(':');
            String payloadId = payloadFilenameMessage.substring(0, colonIndex);
            String filename = payloadFilenameMessage.substring(colonIndex + 1);
            filePayloadFilenames.put(payloadId, filename);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void connectedToEndpoint(Endpoint endpoint) {
        Log.d(TAG, String.format("connectedToEndpoint(endpoint=%s)", endpoint));
        mEstablishedConnections.put(endpoint.getId(), endpoint);
        onEndpointConnected(endpoint);
    }

    private void disconnectedFromEndpoint(Endpoint endpoint) {
        Log.d(TAG, String.format("disconnectedFromEndpoint(endpoint=%s)", endpoint));
        mEstablishedConnections.remove(endpoint.getId());
        onEndpointDisconnected(endpoint);
    }



    protected void onEndpointConnected(Endpoint endpoint) {
        Log.d(TAG, "onEndpointConnected");

        stopDiscovering();
        stopAdvertising();

        listener.onStateUpdate(STATE_READY_SEND_DATA);
    }


    protected void onEndpointDisconnected(Endpoint endpoint) {
        Log.d(TAG, "onEndpointDisconnected");
        Toast.makeText(
                this, "Disconnected", Toast.LENGTH_SHORT)
                .show();
    }

    private static String generateRandomName() {
        String name = "";
        Random random = new Random();
        for (int i = 0; i < 5; i++) {
            name += random.nextInt(10);
        }
        return name;
    }
}
