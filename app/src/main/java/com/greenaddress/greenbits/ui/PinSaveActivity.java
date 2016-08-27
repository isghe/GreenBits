package com.greenaddress.greenbits.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;

import com.dd.CircularProgressButton;
import com.greenaddress.greenbits.KeyStoreAES;

public class PinSaveActivity extends GaActivity {

    private static final int ACTIVITY_REQUEST_CODE = 1;
    private static final String NEW_PIN_MNEMONIC = "com.greenaddress.greenbits.NewPinMnemonic";

    private CheckBox mNativeAuthCB;
    private EditText mPinText;
    private Button mSkipButton;
    private CircularProgressButton mSaveButton;

    static public Intent createIntent(Context ctx, final String mnemonic) {
        final Intent intent = new Intent(ctx, PinSaveActivity.class);
        intent.putExtra(NEW_PIN_MNEMONIC, mnemonic);
        return intent;
    }

    private void setPin(final String pin, final boolean isNative) {

        if (pin.length() < 4) {
            shortToast(R.string.err_pin_save_wrong_length);
            return;
        }

        final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mPinText.getWindowToken(), 0);
        final String mnemonic = getIntent().getStringExtra(NEW_PIN_MNEMONIC);

        mSaveButton.setIndeterminateProgressMode(true);
        mSaveButton.setProgress(50);
        mPinText.setEnabled(false);
        UI.hide(mSkipButton);
        CB.after(mService.setPin(mnemonic, pin),
                new CB.Op<Void>(this) {
                    @Override
                    public void onSuccess(final Void result) {
                        setResult(RESULT_OK);
                        if (!isNative) {
                            // The user has set a non-native PIN.
                            // In case they already had a native PIN they are overriding,
                            // blank the native value so future logins don't detect it.
                            KeyStoreAES.wipePIN(mService);
                        }
                        finishOnUiThread();
                    }

                    @Override
                    public void onUiFailure(final Throwable t) {
                        mSaveButton.setProgress(0);
                        mPinText.setEnabled(true);
                        UI.show(mSkipButton);
                    }
                }, mService.getExecutor());
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {

        if (requestCode == ACTIVITY_REQUEST_CODE && resultCode == RESULT_OK &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Challenge completed, proceed with using cipher
            tryEncrypt();
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    void tryEncrypt() {
        try {
            setPin(KeyStoreAES.tryEncrypt(mService), true);
        } catch (final KeyStoreAES.RequiresAuthenticationScreen e) {
            KeyStoreAES.showAuthenticationScreen(this);
        } catch (final KeyStoreAES.KeyInvalidated e) {
            toast("Problem with key " + e.getMessage());
        }
    }

    @Override
    protected int getMainViewId() { return R.layout.activity_pin_save; }

    @Override
    protected void onCreateWithService(final Bundle savedInstanceState) {

        mPinText = UI.find(this, R.id.pinSaveText);
        mNativeAuthCB = UI.find(this, R.id.useNativeAuthentication);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                KeyStoreAES.createKey(true);

                UI.show(mNativeAuthCB);
                mNativeAuthCB.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(final CompoundButton compoundButton, final boolean isChecked) {
                        if (isChecked)
                            tryEncrypt();
                    }
                });

            } catch (final RuntimeException e) {
                // lock not set, simply don't show native options
            }
        }

        mPinText.setOnEditorActionListener(
                UI.getListenerRunOnEnter(new Runnable() {
                    public void run() {
                        setPin(UI.getText(mPinText), false);
                    }
                }));

        mSaveButton = (CircularProgressButton) UI.mapClick(this, R.id.pinSaveButton, new View.OnClickListener() {
            public void onClick(final View v) {
                setPin(UI.getText(mPinText), false);
            }
        });

        mSkipButton = (Button) UI.mapClick(this, R.id.pinSkipButton, new View.OnClickListener() {
            public void onClick(final View v) {
                setResult(RESULT_CANCELED); // Skip
                finish();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.common_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return item.getItemId() == R.id.action_settings || super.onOptionsItemSelected(item);
    }
}
