package com.greenaddress.greenbits.ui;

import android.annotation.SuppressLint;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import com.greenaddress.greenbits.GaService;

import org.bitcoinj.core.Monetary;
import org.bitcoinj.utils.MonetaryFormat;

import java.util.ArrayList;

class AccountItemAdapter extends RecyclerView.Adapter<AccountItemAdapter.Item> {

    private final ArrayList<String> mNames;
    private final ArrayList<Integer> mPointers;
    private OnAccountSelected mOnAccountSelected;
    private final GaService mService;

    interface OnAccountSelected {
        void onAccountSelected(int account);
    }

    public AccountItemAdapter(final ArrayList<String> names, final ArrayList<Integer> pointers, final GaService service) {
        mNames = names;
        mPointers = pointers;
        mService = service;
    }

    void setCallback(final OnAccountSelected cb) {
        mOnAccountSelected = cb;
    }

    @Override
    public Item onCreateViewHolder(final ViewGroup parent, final int viewType) {
        final View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.dialog_accountlistitem, parent, false);
        return new Item(view);
    }

    private void onDisplayBalance(final Item holder, final int position) {
        final Monetary monetary = mService.getCoinBalance(mPointers.get(position));
        final String btcUnit = (String) mService.getUserConfig("unit");
        final MonetaryFormat bitcoinFormat = CurrencyMapper.mapBtcUnitToFormat(btcUnit);

        if (btcUnit == null || btcUnit.equals("bits")) {
            holder.mBalanceDenomination.setText("bits ");
            holder.mBalanceDenominationIcon.setText("");
        } else {
            holder.mBalanceDenomination.setText(Html.fromHtml(CurrencyMapper.mapBtcUnitToPrefix(btcUnit)));
            holder.mBalanceDenominationIcon.setText(Html.fromHtml("&#xf15a; "));
        }

        final String btcBalance = bitcoinFormat.noCode().format(monetary).toString();
        UI.setAmountText(holder.mBalance, btcBalance);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(final Item holder, final int position) {
        holder.mName.setText(mNames.get(position));
        onDisplayBalance(holder, position);
        if (mPointers.get(position) == mService.getCurrentSubAccount())
            holder.mRadio.setChecked(true);
        holder.mView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                mOnAccountSelected.onAccountSelected(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mNames.size();
    }

    public static class Item extends RecyclerView.ViewHolder {

        final RadioButton mRadio;
        final TextView mBalance;
        final TextView mBalanceDenomination;
        final TextView mBalanceDenominationIcon;

        final View mView;

        final TextView mName;

        public Item(final View v) {
            super(v);
            mView = v;
            mRadio = UI.find(v, R.id.radio);
            mName = UI.find(v, R.id.name);
            mBalance = UI.find(v, R.id.mainBalanceText);
            mBalanceDenominationIcon = UI.find(v, R.id.mainBalanceBitcoinIcon);
            mBalanceDenomination = UI.find(v, R.id.mainBitcoinScaleText);
        }
    }
}