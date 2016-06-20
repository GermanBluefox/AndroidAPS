package info.nightscout.androidaps.plugins.Overview.Dialogs;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.text.DecimalFormat;

import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Result;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.TempBasalsInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.OpenAPSMA.IobTotal;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.*;

public class WizardDialog extends DialogFragment implements OnClickListener {

    Button wizardDialogDeliverButton;
    TextView correctionInput;
    TextView carbsInput;
    TextView bgInput;
    TextView bg, bgInsulin, bgUnits;
    CheckBox bgCheckbox;
    TextView carbs, carbsInsulin;
    TextView iob, iobInsulin;
    CheckBox iobCheckbox;
    TextView correctionInsulin;
    TextView total, totalInsulin;

    public static final DecimalFormat numberFormat = new DecimalFormat("0.00");
    public static final DecimalFormat intFormat = new DecimalFormat("0");

    Integer calculatedCarbs = 0;
    Double calculatedTotalInsulin = 0d;

    final private TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void afterTextChanged(Editable s) {}
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override
        public void onTextChanged(CharSequence s, int start,int before, int count) {
            calculateInsulin();
        }
    };

    final CompoundButton.OnCheckedChangeListener onCheckedChangeListener = new CompoundButton.OnCheckedChangeListener()
    {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
            calculateInsulin();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.overview_wizard_fragment, null, false);

        wizardDialogDeliverButton = (Button) view.findViewById(R.id.treatments_wizard_deliverButton);
        wizardDialogDeliverButton.setOnClickListener(this);

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        correctionInput = (TextView)view.findViewById(R.id.treatments_wizard_correctioninput);
        carbsInput = (TextView)view.findViewById(R.id.treatments_wizard_carbsinput);
        bgInput = (TextView)view.findViewById(R.id.treatments_wizard_bginput);

        correctionInput.addTextChangedListener(textWatcher);
        carbsInput.addTextChangedListener(textWatcher);
        bgInput.addTextChangedListener(textWatcher);

        bg = (TextView)view.findViewById(R.id.treatments_wizard_bg);
        bgInsulin = (TextView)view.findViewById(R.id.treatments_wizard_bginsulin);
        bgUnits = (TextView)view.findViewById(R.id.treatments_wizard_bgunits);
        bgCheckbox = (CheckBox) view.findViewById(R.id.treatments_wizard_bgcheckbox);
        carbs = (TextView)view.findViewById(R.id.treatments_wizard_carbs);
        carbsInsulin = (TextView)view.findViewById(R.id.treatments_wizard_carbsinsulin);
        iob = (TextView)view.findViewById(R.id.treatments_wizard_iob);
        iobInsulin = (TextView)view.findViewById(R.id.treatments_wizard_iobinsulin);
        iobCheckbox = (CheckBox) view.findViewById(R.id.treatments_wizard_iobcheckbox);
        correctionInsulin = (TextView)view.findViewById(R.id.treatments_wizard_correctioninsulin);
        total = (TextView)view.findViewById(R.id.treatments_wizard_total);
        totalInsulin = (TextView)view.findViewById(R.id.treatments_wizard_totalinsulin);

        bgCheckbox.setOnCheckedChangeListener(onCheckedChangeListener);
        iobCheckbox.setOnCheckedChangeListener(onCheckedChangeListener);

        initDialog();
        return view;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.treatments_wizard_deliverButton:
                if (calculatedTotalInsulin > 0d || calculatedCarbs > 0d){
                    DecimalFormat formatNumber2decimalplaces = new DecimalFormat("0.00");
                    String confirmMessage = getString(R.string.entertreatmentquestion);

                    Double insulinAfterConstraints = MainActivity.getConfigBuilder().applyBolusConstraints(calculatedTotalInsulin);
                    Integer carbsAfterConstraints = MainActivity.getConfigBuilder().applyCarbsConstraints(calculatedCarbs);

                    confirmMessage += "\n" + getString(R.string.bolus) + ": " + formatNumber2decimalplaces.format(insulinAfterConstraints) + "U";
                    confirmMessage += "\n" + getString(R.string.carbs) + ": " + carbsAfterConstraints + "g";

                    if (insulinAfterConstraints != calculatedTotalInsulin || carbsAfterConstraints != calculatedCarbs) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                        builder.setTitle(getContext().getString(R.string.treatmentdeliveryerror));
                        builder.setMessage(getString(R.string.constraints_violation) + "\n" + getString(R.string.changeyourinput));
                        builder.setPositiveButton(getContext().getString(R.string.ok), null);
                        builder.show();
                        return;
                    }

                    final Double finalInsulinAfterConstraints = insulinAfterConstraints;
                    final Integer finalCarbsAfterConstraints = carbsAfterConstraints;

                    AlertDialog.Builder builder = new AlertDialog.Builder(this.getContext());
                    builder.setTitle(this.getContext().getString(R.string.confirmation));
                    builder.setMessage(confirmMessage);
                    builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            if (finalInsulinAfterConstraints > 0 || finalCarbsAfterConstraints > 0) {
                                PumpInterface pump = MainActivity.getConfigBuilder().getActivePump();
                                Result result = pump.deliverTreatment(finalInsulinAfterConstraints, finalCarbsAfterConstraints);
                                if (!result.success) {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                    builder.setTitle(getContext().getString(R.string.treatmentdeliveryerror));
                                    builder.setMessage(result.comment);
                                    builder.setPositiveButton(getContext().getString(R.string.ok), null);
                                    builder.show();
                                }
                            }
                        }
                    });
                    builder.setNegativeButton(getString(R.string.cancel), null);
                    builder.show();
                    dismiss();
                }
                break;
        }

    }

    private void initDialog() {
        NSProfile profile = MainActivity.getConfigBuilder().getActiveProfile().getProfile();

        if (profile == null) {
            ToastUtils.showToastInUiThread(MainApp.instance().getApplicationContext(), "No profile loaded from NS yet");
            dismiss();
            return;
        }

        String units = profile.getUnits();
        bgUnits.setText(units);

        // Set BG if not old
        BgReading lastBg = MainApp.getDbHelper().lastBg();
        Double lastBgValue = lastBg.valueToUnits(units);

        if (lastBg != null) {
            Double sens = profile.getIsf(NSProfile.secondsFromMidnight());
            Double targetBGLow  = profile.getTargetLow(NSProfile.secondsFromMidnight());
            Double targetBGHigh  = profile.getTargetHigh(NSProfile.secondsFromMidnight());
            Double bgDiff;
            if (lastBgValue <= targetBGLow) {
                bgDiff = lastBgValue - targetBGLow;
            } else {
                bgDiff = lastBgValue - targetBGHigh;
            }

            bg.setText(lastBg.valueToUnitsToString(units) + " ISF: " + intFormat.format(sens));
            bgInsulin.setText(numberFormat.format(bgDiff / sens) + "U");
            bgInput.setText(lastBg.valueToUnitsToString(units));
        } else {
            bg.setText("");
            bgInsulin.setText("");
            bgInput.setText("");
        }

        // IOB calculation
        TreatmentsInterface treatments = MainActivity.getConfigBuilder().getActiveTreatments();
        TempBasalsInterface tempBasals = MainActivity.getConfigBuilder().getActiveTempBasals();
        treatments.updateTotalIOB();
        tempBasals.updateTotalIOB();
        IobTotal bolusIob = treatments.getLastCalculation();
        IobTotal basalIob = tempBasals.getLastCalculation();

        Double iobTotal = bolusIob.iob + basalIob.iob;
        iobInsulin.setText("-" + numberFormat.format(iobTotal) + "U");

        totalInsulin.setText("");
        wizardDialogDeliverButton.setVisibility(Button.GONE);

    }

    private void calculateInsulin() {
        NSProfile profile = MainActivity.getConfigBuilder().getActiveProfile().getProfile();

        // Entered values
        String i_bg = this.bgInput.getText().toString().replace("," , ".");
        String i_carbs = this.carbsInput.getText().toString().replace(",", ".");
        String i_correction = this.correctionInput.getText().toString().replace(",", ".");
        Double c_bg = 0d;
        try { c_bg = Double.parseDouble(i_bg.equals("") ? "0" : i_bg); } catch (Exception e) {}
        Integer c_carbs = 0;
        try { c_carbs = Integer.parseInt(i_carbs.equals("") ? "0" : i_carbs); } catch (Exception e) {}
        c_carbs = (Integer) Math.round(c_carbs);
        Double c_correction = 0d;
        try { c_correction = Double.parseDouble(i_correction.equals("") ? "0" : i_correction);  } catch (Exception e) {}
        if(c_correction != MainActivity.getConfigBuilder().applyBolusConstraints(c_correction)) {
            this.correctionInput.setText("");
            wizardDialogDeliverButton.setVisibility(Button.GONE);
            return;
        }
        if(c_carbs != MainActivity.getConfigBuilder().applyCarbsConstraints(c_carbs)) {
            this.carbsInput.setText("");
            wizardDialogDeliverButton.setVisibility(Button.GONE);
            return;
        }


        // Insulin from BG
        Double sens = profile.getIsf(NSProfile.secondsFromMidnight());
        Double targetBGLow  = profile.getTargetLow(NSProfile.secondsFromMidnight());
        Double targetBGHigh  = profile.getTargetHigh(NSProfile.secondsFromMidnight());
        Double bgDiff;
        if (c_bg <= targetBGLow) {
            bgDiff = c_bg - targetBGLow;
        } else {
            bgDiff = c_bg - targetBGHigh;
        }
        Double insulinFromBG = (bgCheckbox.isChecked() && c_bg != 0d) ? bgDiff /sens : 0d;
        bg.setText(c_bg + " ISF: " + intFormat.format(sens));
        bgInsulin.setText(numberFormat.format(insulinFromBG) + "U");

        // Insuling from carbs
        Double ic = profile.getIc(NSProfile.secondsFromMidnight());
        Double insulinFromCarbs = c_carbs / ic;
        carbs.setText(intFormat.format(c_carbs) + "g IC: " + intFormat.format(ic));
        carbsInsulin.setText(numberFormat.format(insulinFromCarbs) + "U");

        // Insulin from IOB
        TreatmentsInterface treatments = MainActivity.getConfigBuilder().getActiveTreatments();
        TempBasalsInterface tempBasals = MainActivity.getConfigBuilder().getActiveTempBasals();
        treatments.updateTotalIOB();
        tempBasals.updateTotalIOB();
        IobTotal bolusIob = treatments.getLastCalculation();
        IobTotal basalIob = tempBasals.getLastCalculation();

        Double iobTotal = bolusIob.iob + basalIob.iob;
        Double insulingFromIOB = iobCheckbox.isChecked() ? iobTotal : 0d;
        iobInsulin.setText("-" + numberFormat.format(insulingFromIOB) + "U");

        // Insulin from correction
        Double insulinFromCorrection = c_correction;
        correctionInsulin.setText(numberFormat.format(insulinFromCorrection) + "U");

        // Total
        calculatedTotalInsulin = insulinFromBG + insulinFromCarbs - insulingFromIOB + insulinFromCorrection;

        if (calculatedTotalInsulin < 0) {
            Double carbsEquivalent = -calculatedTotalInsulin * ic;
            total.setText("Missing " + intFormat.format(carbsEquivalent) + "g");
            calculatedTotalInsulin = 0d;
            totalInsulin.setText("");
        } else {
            calculatedTotalInsulin = Round.roundTo(calculatedTotalInsulin, 0.05d);
            total.setText("");
            totalInsulin.setText(numberFormat.format(calculatedTotalInsulin) + "U");
        }

        calculatedCarbs = c_carbs;

        if (calculatedTotalInsulin > 0d || calculatedCarbs > 0d) {
            String insulinText = calculatedTotalInsulin > 0d ? (numberFormat.format(calculatedTotalInsulin) + "U") : "";
            String carbsText = calculatedCarbs > 0d ? (intFormat.format(calculatedCarbs) + "g") : "";
            wizardDialogDeliverButton.setText("SEND " + insulinText + " " + carbsText);
            wizardDialogDeliverButton.setVisibility(Button.VISIBLE);
        } else {
            wizardDialogDeliverButton.setVisibility(Button.GONE);
        }
    }
}