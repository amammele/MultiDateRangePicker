
package com.amammele.multidaterangepicker;

import android.animation.LayoutTransition;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.amammele.multidaterangepicker.datepicker.MultiDatePicker;
import com.amammele.multidaterangepicker.common.ButtonHandler;
import com.amammele.multidaterangepicker.datepicker.SelectedDate;
import com.amammele.multidaterangepicker.drawables.OverflowDrawable;
import com.amammele.multidaterangepicker.helpers.MultiDatePickerListenerAdapter;
import com.amammele.multidaterangepicker.helpers.MultiDateOptions;
import com.amammele.multidaterangepicker.recurrencepicker.MultiDateRecurrencePicker;
import com.amammele.multidaterangepicker.timepicker.MultiDateTimePicker;
import com.amammele.multidaterangepicker.utilities.SUtils;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A customizable view that provisions picking of a date,
 * time and recurrence option, all from a single user-interface.
 * You can also view 'MultiDatePicker' as a collection of
 * material-styled (API 23) DatePicker, TimePicker
 * and RecurrencePicker, backported to API 14.
 * You can opt for any combination of these three Pickers.
 */
public class MultiDateRangePicker extends FrameLayout
        implements MultiDatePicker.OnDateChangedListener,
        MultiDatePicker.DatePickerValidationCallback,
        MultiDateTimePicker.TimePickerValidationCallback {
    private static final String TAG = MultiDateRangePicker.class.getSimpleName();

    // Used for formatting date range
    private static final long MONTH_IN_MILLIS = DateUtils.YEAR_IN_MILLIS / 12;

    // Container for 'MulitDatePicker' & 'MultiDateTimePicker'
    private LinearLayout llMainContentHolder;

    // For access to 'MultiDateRecurrencePicker'
    private ImageView ivRecurrenceOptionsDP, ivRecurrenceOptionsTP;

    // Recurrence picker options
    private MultiDateRecurrencePicker mMultiDateRecurrencePicker;
    private MultiDateRecurrencePicker.RecurrenceOption mCurrentRecurrenceOption
            = MultiDateRecurrencePicker.RecurrenceOption.DOES_NOT_REPEAT;
    private String mRecurrenceRule;

    // Keeps track which picker is showing
    private MultiDateOptions.Picker mCurrentPicker, mHiddenPicker;

    // Date picker
    private MultiDatePicker mDatePicker;

    // Time picker
    private MultiDateTimePicker mTimePicker;

    // Callback
    private MultiDatePickerListenerAdapter mListener;

    // Client-set options
    private MultiDateOptions mOptions;

    // Ok, cancel & switch button handler
    private ButtonHandler mButtonLayout;

    // Flags set based on client-set options {MultiDateOptions}
    private boolean mDatePickerValid = true, mTimePickerValid = true,
            mDatePickerEnabled, mTimePickerEnabled, mRecurrencePickerEnabled,
            mDatePickerSyncStateCalled;

    // Used if listener returns
    // null/invalid(zero-length, empty) string
    private DateFormat mDefaultDateFormatter, mDefaultTimeFormatter;

    // Listener for recurrence picker
    private final MultiDateRecurrencePicker.OnRepeatOptionSetListener mRepeatOptionSetListener = new MultiDateRecurrencePicker.OnRepeatOptionSetListener() {
        @Override
        public void onRepeatOptionSet(MultiDateRecurrencePicker.RecurrenceOption option, String recurrenceRule) {
            mCurrentRecurrenceOption = option;
            mRecurrenceRule = recurrenceRule;
            onDone();
        }

        @Override
        public void onDone() {
            if (mDatePickerEnabled || mTimePickerEnabled) {
                updateCurrentPicker();
                updateDisplay();
            } else { /* No other picker is activated. Dismiss. */
                mButtonLayoutCallback.onOkay();
            }
        }
    };

    // Handle ok, cancel & switch button click events
    private final ButtonHandler.Callback mButtonLayoutCallback = new ButtonHandler.Callback() {
        @Override
        public void onOkay() {
            SelectedDate selectedDate = null;

            if (mDatePickerEnabled) {
                selectedDate = mDatePicker.getSelectedDate();
            }

            int hour = -1, minute = -1;

            if (mTimePickerEnabled) {
                hour = mTimePicker.getCurrentHour();
                minute = mTimePicker.getCurrentMinute();
            }

            MultiDateRecurrencePicker.RecurrenceOption recurrenceOption
                    = MultiDateRecurrencePicker.RecurrenceOption.DOES_NOT_REPEAT;
            String recurrenceRule = null;

            if (mRecurrencePickerEnabled) {
                recurrenceOption = mCurrentRecurrenceOption;

                if (recurrenceOption == MultiDateRecurrencePicker.RecurrenceOption.CUSTOM) {
                    recurrenceRule = mRecurrenceRule;
                }
            }

            mListener.onDateTimeRecurrenceSet(MultiDateRangePicker.this,
                    // DatePicker
                    selectedDate,
                    // TimePicker
                    hour, minute,
                    // RecurrencePicker
                    recurrenceOption, recurrenceRule);
        }

        @Override
        public void onCancel() {
            mListener.onCancelled();
        }

        @Override
        public void onSwitch() {
            mCurrentPicker = mCurrentPicker == MultiDateOptions.Picker.DATE_PICKER ?
                    MultiDateOptions.Picker.TIME_PICKER
                    : MultiDateOptions.Picker.DATE_PICKER;

            updateDisplay();
        }
    };

    public MultiDateRangePicker(Context context) {
        this(context, null);
    }

    public MultiDateRangePicker(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.mdPickerStyle);
    }

    public MultiDateRangePicker(Context context, AttributeSet attrs, int defStyleAttr) {
        super(createThemeWrapper(context), attrs, defStyleAttr);
        initializeLayout();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public MultiDateRangePicker(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(createThemeWrapper(context), attrs, defStyleAttr, defStyleRes);
        initializeLayout();
    }

    private static ContextThemeWrapper createThemeWrapper(Context context) {
        final TypedArray forParent = context.obtainStyledAttributes(
                new int[]{R.attr.mdPickerStyle});
        int parentStyle = forParent.getResourceId(0, R.style.MDPickerStyleLight);
        forParent.recycle();

        return new ContextThemeWrapper(context, parentStyle);
    }

    private void initializeLayout() {
        Context context = getContext();
        SUtils.initializeResources(context);

        LayoutInflater.from(context).inflate(R.layout.md_picker_view_layout,
                this, true);

        mDefaultDateFormatter = DateFormat.getDateInstance(DateFormat.MEDIUM,
                Locale.getDefault());
        mDefaultTimeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT,
                Locale.getDefault());
        mDefaultTimeFormatter.setTimeZone(TimeZone.getTimeZone("GMT+0"));

        llMainContentHolder = (LinearLayout) findViewById(R.id.llMainContentHolder);
        mButtonLayout = new ButtonHandler(this);
        initializeRecurrencePickerSwitch();

        mDatePicker = (MultiDatePicker) findViewById(R.id.datePicker);
        mTimePicker = (MultiDateTimePicker) findViewById(R.id.timePicker);
        mMultiDateRecurrencePicker = (MultiDateRecurrencePicker)
                findViewById(R.id.repeat_option_picker);
    }

    public void initializePicker(MultiDateOptions options, MultiDatePickerListenerAdapter listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null.");
        }

        if (options != null) {
            options.verifyValidity();
        } else {
            options = new MultiDateOptions();
        }

        mOptions = options;
        mListener = listener;

        processOptions();
        updateDisplay();
    }

    // Called before 'RecurrencePicker' is shown
    private void updateHiddenPicker() {
        if (mDatePickerEnabled && mTimePickerEnabled) {
            mHiddenPicker = mDatePicker.getVisibility() == View.VISIBLE ?
                    MultiDateOptions.Picker.DATE_PICKER : MultiDateOptions.Picker.TIME_PICKER;
        } else if (mDatePickerEnabled) {
            mHiddenPicker = MultiDateOptions.Picker.DATE_PICKER;
        } else if (mTimePickerEnabled) {
            mHiddenPicker = MultiDateOptions.Picker.TIME_PICKER;
        } else {
            mHiddenPicker = MultiDateOptions.Picker.INVALID;
        }
    }

    // 'mHiddenPicker' retains the Picker that was active
    // before 'RecurrencePicker' was shown. On its dismissal,
    // we have an option to show either 'DatePicker' or 'TimePicker'.
    // 'mHiddenPicker' helps identify the correct option.
    private void updateCurrentPicker() {
        if (mHiddenPicker != MultiDateOptions.Picker.INVALID) {
            mCurrentPicker = mHiddenPicker;
        } else {
            throw new RuntimeException("Logic issue: No valid option for mCurrentPicker");
        }
    }

    private void updateDisplay() {
        CharSequence switchButtonText;

        if (mCurrentPicker == MultiDateOptions.Picker.DATE_PICKER) {

            if (mTimePickerEnabled) {
                mTimePicker.setVisibility(View.GONE);
            }

            if (mRecurrencePickerEnabled) {
                mMultiDateRecurrencePicker.setVisibility(View.GONE);
            }

            mDatePicker.setVisibility(View.VISIBLE);
            llMainContentHolder.setVisibility(View.VISIBLE);

            if (mButtonLayout.isSwitcherButtonEnabled()) {
                Date toFormat = new Date(mTimePicker.getCurrentHour() * DateUtils.HOUR_IN_MILLIS
                        + mTimePicker.getCurrentMinute() * DateUtils.MINUTE_IN_MILLIS);

                switchButtonText = mListener.formatTime(toFormat);

                if (TextUtils.isEmpty(switchButtonText)) {
                    switchButtonText = mDefaultTimeFormatter.format(toFormat);
                }

                mButtonLayout.updateSwitcherText(MultiDateOptions.Picker.DATE_PICKER, switchButtonText);
            }

            if (!mDatePickerSyncStateCalled) {
                mDatePickerSyncStateCalled = true;
            }
        } else if (mCurrentPicker == MultiDateOptions.Picker.TIME_PICKER) {
            if (mDatePickerEnabled) {
                mDatePicker.setVisibility(View.GONE);
            }

            if (mRecurrencePickerEnabled) {
                mMultiDateRecurrencePicker.setVisibility(View.GONE);
            }

            mTimePicker.setVisibility(View.VISIBLE);
            llMainContentHolder.setVisibility(View.VISIBLE);

            if (mButtonLayout.isSwitcherButtonEnabled()) {
                SelectedDate selectedDate = mDatePicker.getSelectedDate();
                switchButtonText = mListener.formatDate(selectedDate);

                if (TextUtils.isEmpty(switchButtonText)) {
                    if (selectedDate.getType() == SelectedDate.Type.SINGLE) {
                        Date toFormat = new Date(mDatePicker.getSelectedDateInMillis());
                        switchButtonText = mDefaultDateFormatter.format(toFormat);
                    } else if (selectedDate.getType() == SelectedDate.Type.RANGE) {
                        switchButtonText = formatDateRange(selectedDate);
                    }
                }

                mButtonLayout.updateSwitcherText(MultiDateOptions.Picker.TIME_PICKER, switchButtonText);
            }
        } else if (mCurrentPicker == MultiDateOptions.Picker.REPEAT_OPTION_PICKER) {
            updateHiddenPicker();
            mMultiDateRecurrencePicker.updateView();

            if (mDatePickerEnabled || mTimePickerEnabled) {
                llMainContentHolder.setVisibility(View.GONE);
            }

            mMultiDateRecurrencePicker.setVisibility(View.VISIBLE);
        }
    }

    private String formatDateRange(SelectedDate selectedDate) {
        Calendar startDate = selectedDate.getStartDate();
        Calendar endDate = selectedDate.getEndDate();

        startDate.set(Calendar.MILLISECOND, 0);
        startDate.set(Calendar.SECOND, 0);
        startDate.set(Calendar.MINUTE, 0);
        startDate.set(Calendar.HOUR, 0);

        endDate.set(Calendar.MILLISECOND, 0);
        endDate.set(Calendar.SECOND, 0);
        endDate.set(Calendar.MINUTE, 0);
        endDate.set(Calendar.HOUR, 0);
        // Move to next day since we are nulling out the time fields
        endDate.add(Calendar.DAY_OF_MONTH, 1);

        float elapsedTime = endDate.getTimeInMillis() - startDate.getTimeInMillis();

        if (elapsedTime >= DateUtils.YEAR_IN_MILLIS) {
            final float years = elapsedTime / DateUtils.YEAR_IN_MILLIS;

            boolean roundUp = years - (int) years > 0.5f;
            final int yearsVal = roundUp ? (int) (years + 1) : (int) years;

            return "~" + yearsVal + " " + (yearsVal == 1 ? "year" : "years");
        } else if (elapsedTime >= MONTH_IN_MILLIS) {
            final float months = elapsedTime / MONTH_IN_MILLIS;

            boolean roundUp = months - (int) months > 0.5f;
            final int monthsVal = roundUp ? (int) (months + 1) : (int) months;

            return "~" + monthsVal + " " + (monthsVal == 1 ? "month" : "months");
        } else {
            final float days = elapsedTime / DateUtils.DAY_IN_MILLIS;

            boolean roundUp = days - (int) days > 0.5f;
            final int daysVal = roundUp ? (int) (days + 1) : (int) days;

            return "~" + daysVal + " " + (daysVal == 1 ? "day" : "days");
        }
    }

    private void initializeRecurrencePickerSwitch() {
        ivRecurrenceOptionsDP = (ImageView) findViewById(R.id.ivRecurrenceOptionsDP);
        ivRecurrenceOptionsTP = (ImageView) findViewById(R.id.ivRecurrenceOptionsTP);

        int iconColor, pressedStateBgColor;

        TypedArray typedArray = getContext().obtainStyledAttributes(R.styleable.MultiDateRangePicker);
        try {
            iconColor = typedArray.getColor(R.styleable.MultiDateRangePicker_mdOverflowIconColor,
                    SUtils.COLOR_TEXT_PRIMARY_INVERSE);
            pressedStateBgColor = typedArray.getColor(R.styleable.MultiDateRangePicker_mdOverflowIconPressedBgColor,
                    SUtils.COLOR_TEXT_PRIMARY);
        } finally {
            typedArray.recycle();
        }

        ivRecurrenceOptionsDP.setImageDrawable(
                new OverflowDrawable(getContext(), iconColor));
        SUtils.setViewBackground(ivRecurrenceOptionsDP,
                SUtils.createOverflowButtonBg(pressedStateBgColor));

        ivRecurrenceOptionsTP.setImageDrawable(
                new OverflowDrawable(getContext(), iconColor));
        SUtils.setViewBackground(ivRecurrenceOptionsTP,
                SUtils.createOverflowButtonBg(pressedStateBgColor));

        ivRecurrenceOptionsDP.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mCurrentPicker = MultiDateOptions.Picker.REPEAT_OPTION_PICKER;
                updateDisplay();
            }
        });

        ivRecurrenceOptionsTP.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mCurrentPicker = MultiDateOptions.Picker.REPEAT_OPTION_PICKER;
                updateDisplay();
            }
        });
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        return new SavedState(super.onSaveInstanceState(), mCurrentPicker, mHiddenPicker,
                mCurrentRecurrenceOption, mRecurrenceRule);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        BaseSavedState bss = (BaseSavedState) state;
        super.onRestoreInstanceState(bss.getSuperState());
        SavedState ss = (SavedState) bss;

        mCurrentPicker = ss.getCurrentPicker();
        mCurrentRecurrenceOption = ss.getCurrentRepeatOption();
        mRecurrenceRule = ss.getRecurrenceRule();

        mHiddenPicker = ss.getHiddenPicker();
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        super.dispatchRestoreInstanceState(container);
        updateDisplay();
    }

    /**
     * Class for managing state storing/restoring.
     */
    private static class SavedState extends BaseSavedState {

        private final MultiDateOptions.Picker sCurrentPicker, sHiddenPicker /*One of DatePicker/TimePicker*/;
        private final MultiDateRecurrencePicker.RecurrenceOption sCurrentRecurrenceOption;
        private final String sRecurrenceRule;

        /**
         * Constructor called from {@link MultiDateRangePicker#onSaveInstanceState()}
         */
        private SavedState(Parcelable superState, MultiDateOptions.Picker currentPicker,
                           MultiDateOptions.Picker hiddenPicker,
                           MultiDateRecurrencePicker.RecurrenceOption recurrenceOption,
                           String recurrenceRule) {
            super(superState);

            sCurrentPicker = currentPicker;
            sHiddenPicker = hiddenPicker;
            sCurrentRecurrenceOption = recurrenceOption;
            sRecurrenceRule = recurrenceRule;
        }

        /**
         * Constructor called from {@link #CREATOR}
         */
        private SavedState(Parcel in) {
            super(in);

            sCurrentPicker = MultiDateOptions.Picker.valueOf(in.readString());
            sHiddenPicker = MultiDateOptions.Picker.valueOf(in.readString());
            sCurrentRecurrenceOption = MultiDateRecurrencePicker.RecurrenceOption.valueOf(in.readString());
            sRecurrenceRule = in.readString();
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            super.writeToParcel(dest, flags);

            dest.writeString(sCurrentPicker.name());
            dest.writeString(sHiddenPicker.name());
            dest.writeString(sCurrentRecurrenceOption.name());
            dest.writeString(sRecurrenceRule);
        }

        public MultiDateOptions.Picker getCurrentPicker() {
            return sCurrentPicker;
        }

        public MultiDateOptions.Picker getHiddenPicker() {
            return sHiddenPicker;
        }

        public MultiDateRecurrencePicker.RecurrenceOption getCurrentRepeatOption() {
            return sCurrentRecurrenceOption;
        }

        public String getRecurrenceRule() {
            return sRecurrenceRule;
        }

        @SuppressWarnings("all")
        // suppress unused and hiding
        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {

            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    private void processOptions() {
        if (mOptions.animateLayoutChanges()) {
            // Basic Layout Change Animation(s)
            LayoutTransition layoutTransition = new LayoutTransition();
            if (SUtils.isApi_16_OrHigher()) {
                layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
            }
            setLayoutTransition(layoutTransition);
        } else {
            setLayoutTransition(null);
        }

        mDatePickerEnabled = mOptions.isDatePickerActive();
        mTimePickerEnabled = mOptions.isTimePickerActive();
        mRecurrencePickerEnabled = mOptions.isRecurrencePickerActive();

        if (mDatePickerEnabled) {
            //int[] dateParams = mOptions.getDateParams();
            //mDatePicker.init(dateParams[0] /* year */,
            //        dateParams[1] /* month of year */,
            //        dateParams[2] /* day of month */,
            //        mOptions.canPickDateRange(),
            //        this);
            mDatePicker.init(mOptions.getDateParams(), mOptions.canPickDateRange(), mOptions.canToggleRangeWithoutLongPress(), this);

            long[] dateRange = mOptions.getDateRange();

            if (dateRange[0] /* min date */ != Long.MIN_VALUE) {
                mDatePicker.setMinDate(dateRange[0]);
            }

            if (dateRange[1] /* max date */ != Long.MIN_VALUE) {
                mDatePicker.setMaxDate(dateRange[1]);
            }

            mDatePicker.setValidationCallback(this);

            ivRecurrenceOptionsDP.setVisibility(mRecurrencePickerEnabled ?
                    View.VISIBLE : View.GONE);
        } else {
            llMainContentHolder.removeView(mDatePicker);
            mDatePicker = null;
        }

        if (mTimePickerEnabled) {
            int[] timeParams = mOptions.getTimeParams();
            mTimePicker.setCurrentHour(timeParams[0] /* hour of day */);
            mTimePicker.setCurrentMinute(timeParams[1] /* minute */);
            mTimePicker.setIs24HourView(mOptions.is24HourView());
            mTimePicker.setValidationCallback(this);

            ivRecurrenceOptionsTP.setVisibility(mRecurrencePickerEnabled ?
                    View.VISIBLE : View.GONE);
        } else {
            llMainContentHolder.removeView(mTimePicker);
            mTimePicker = null;
        }

        if (mDatePickerEnabled && mTimePickerEnabled) {
            mButtonLayout.applyOptions(true /* show switch button */,
                    mButtonLayoutCallback);
        } else {
            mButtonLayout.applyOptions(false /* hide switch button */,
                    mButtonLayoutCallback);
        }

        if (!mDatePickerEnabled && !mTimePickerEnabled) {
            removeView(llMainContentHolder);
            llMainContentHolder = null;
            mButtonLayout = null;
        }

        mCurrentRecurrenceOption = mOptions.getRecurrenceOption();
        mRecurrenceRule = mOptions.getRecurrenceRule();

        if (mRecurrencePickerEnabled) {
            Calendar cal = mDatePickerEnabled ?
                    mDatePicker.getSelectedDate().getStartDate()
                    : SUtils.getCalendarForLocale(null, Locale.getDefault());

            mMultiDateRecurrencePicker.initializeData(mRepeatOptionSetListener,
                    mCurrentRecurrenceOption, mRecurrenceRule,
                    cal.getTimeInMillis());
        } else {
            removeView(mMultiDateRecurrencePicker);
            mMultiDateRecurrencePicker = null;
        }

        mCurrentPicker = mOptions.getPickerToShow();
        // Updated from 'updateDisplay()' when 'RecurrencePicker' is chosen
        mHiddenPicker = MultiDateOptions.Picker.INVALID;
    }

    private void reassessValidity() {
        mButtonLayout.updateValidity(mDatePickerValid && mTimePickerValid);
    }

    @Override
    public void onDateChanged(MultiDatePicker view, SelectedDate selectedDate) {
        // TODO: Consider removing this propagation of date change event altogether
        //mDatePicker.init(selectedDate.getStartDate().get(Calendar.YEAR),
                //selectedDate.getStartDate().get(Calendar.MONTH),
                //selectedDate.getStartDate().get(Calendar.DAY_OF_MONTH),
                //mOptions.canPickDateRange(), this);
        mDatePicker.init(selectedDate, mOptions.canPickDateRange(), mOptions.canToggleRangeWithoutLongPress(), this);
    }

    @Override
    public void onDatePickerValidationChanged(boolean valid) {
        mDatePickerValid = valid;
        reassessValidity();
    }

    @Override
    public void onTimePickerValidationChanged(boolean valid) {
        mTimePickerValid = valid;
        reassessValidity();
    }
}
