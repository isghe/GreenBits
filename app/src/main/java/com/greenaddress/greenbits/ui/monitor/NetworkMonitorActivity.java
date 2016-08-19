package com.greenaddress.greenbits.ui.monitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.greenaddress.greenapi.Network;
import com.greenaddress.greenbits.GaService;
import com.greenaddress.greenbits.ui.FirstScreenActivity;
import com.greenaddress.greenbits.ui.GaActivity;
import com.greenaddress.greenbits.ui.R;
import com.greenaddress.greenbits.ui.UI;

import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.bitcoinj.core.listeners.PeerDisconnectedEventListener;

import java.util.ArrayList;
import java.util.Iterator;

public final class NetworkMonitorActivity extends GaActivity implements PeerConnectedEventListener, PeerDisconnectedEventListener
{
    private final ArrayList<PrettyPeer> mPeers = new ArrayList<>();
    private ArrayAdapter<PrettyPeer> mPeerListAdapter;
    private String mBloomInfo = "";

    @Override
    protected int getMainViewId() { return R.layout.activity_network; }

    @Override
    protected void onCreateWithService(final Bundle savedInstanceState) {
        final ListView peerList = UI.find(this, R.id.peerlistview);
        final TextView emptyText = UI.find(this, R.id.empty_list_view);
        peerList.setEmptyView(emptyText);
    }

    @Override
    public void onPauseWithService() {

        unregisterReceiver(uiUpdated);
        final PeerGroup peerGroup = mService.getSPVPeerGroup();
        if (peerGroup != null) {
            peerGroup.removeConnectedEventListener(this);
            peerGroup.removeDisconnectedEventListener(this);
        }

        mPeers.clear();
    }

    @Override
    public void onResumeWithService() {

        registerReceiver(uiUpdated, new IntentFilter("PEERGROUP_UPDATED"));

        if (mService.isForcedOff()) {
            // FIXME: Should pass flag to activity so it shows it was forced logged out
            startActivity(new Intent(this, FirstScreenActivity.class));
            finish();
            return;
        }

        mPeers.clear();

        final PeerGroup peerGroup = mService.getSPVPeerGroup();
        if (peerGroup == null || !peerGroup.isRunning())
            return;

        for (final Peer peer : peerGroup.getConnectedPeers())
            mPeers.add(new PrettyPeer(peer));

        if (mPeers.size() > 0)
            mBloomInfo = mPeers.get(0).mPeer.getBloomFilter().toString();
        else
            mBloomInfo = getString(R.string.network_monitor_bloom_info);

        final int currentBlock = mService.getCurrentBlock();
        final int spvHeight = mService.getSPVHeight();
        final TextView bloomInfoText = UI.find(this, R.id.bloominfo);
        bloomInfoText.setText(getString(R.string.network_monitor_banner, mBloomInfo, currentBlock - spvHeight));

        mPeerListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mPeers);
        final ListView peerList = UI.find(this, R.id.peerlistview);
        peerList.setAdapter(mPeerListAdapter);

        peerGroup.addConnectedEventListener(this);
        peerGroup.addDisconnectedEventListener(this);
    }

    @Override
    public synchronized void onPeerConnected(final Peer peer, final int peerCount) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPeers.add(new PrettyPeer(peer));
                mPeerListAdapter.notifyDataSetChanged();

                mBloomInfo = peer.getBloomFilter().toString();
                final TextView bloomInfoText = UI.find(NetworkMonitorActivity.this, R.id.bloominfo);
                bloomInfoText.setText(mBloomInfo);
            }
        });
    }

    @Override
    public synchronized void onPeerDisconnected(final Peer peer, int peerCount) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (Iterator<PrettyPeer> it = mPeers.iterator(); it.hasNext(); ) {
                    if (peer == it.next().mPeer)
                        it.remove();
                }
                mPeerListAdapter.notifyDataSetChanged();
            }
        });
    }

    private final BroadcastReceiver uiUpdated = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {

            final String peerGroupIntent = intent.getExtras().getString("peergroup");
            if (peerGroupIntent == null || !peerGroupIntent.equals("stopSPVSync"))
                return;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mPeers.clear();
                    mPeerListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    private class PrettyPeer {
        final Peer mPeer;

        public PrettyPeer(final Peer peer) {
            mPeer = peer;
        }

        public String toString(){
            String ipAddr = mPeer.toString();
            if (ipAddr.length() >= 11 && ipAddr.substring(0,11).equals("[127.0.0.1]")) {
                // FIXME: This is obviously not right if multiple peers are present
                ipAddr = mService.getTrustedPeers();
                if (!ipAddr.isEmpty())
                    ipAddr = mService.createSPVNode(ipAddr).toString();
            }
            return String.format("%s\n%s\n%s\n%s", getString(R.string.network_monitor_peer_addr, ipAddr),
                    getString(R.string.network_monitor_peer_version, mPeer.getPeerVersionMessage().subVer),
                    getString(R.string.network_monitor_peer_block, mPeer.getBestHeight()),
                    getString(R.string.network_monitor_peer_ping, mPeer.getLastPingTime()));
        }
    }
}
