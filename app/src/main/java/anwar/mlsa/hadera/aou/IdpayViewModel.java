package anwar.mlsa.hadera.aou;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Map;

import anwar.mlsa.hadera.aou.domain.use_case.GetExchangeRateUseCase;
import anwar.mlsa.hadera.aou.domain.use_case.SendTransactionUseCase;
import anwar.mlsa.hadera.aou.domain.use_case.VerifyAccountUseCase;
import anwar.mlsa.hadera.aou.domain.util.Result;

public class IdpayViewModel extends AndroidViewModel {

    private final VerifyAccountUseCase verifyAccountUseCase;
    private final SendTransactionUseCase sendTransactionUseCase;
    private final GetExchangeRateUseCase getExchangeRateUseCase;

    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> recipientError = new MutableLiveData<>();
    private final MutableLiveData<String> amountError = new MutableLiveData<>();
    private final MutableLiveData<String> verifiedRecipient = new MutableLiveData<>();
    private final MutableLiveData<Result<Map<String, Object>>> transactionResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isSendButtonEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<String> exchangeRate = new MutableLiveData<>();

    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable debounceRunnable;

    public IdpayViewModel(@NonNull Application application,
                            VerifyAccountUseCase verifyAccountUseCase,
                            SendTransactionUseCase sendTransactionUseCase,
                            GetExchangeRateUseCase getExchangeRateUseCase) {
        super(application);
        this.verifyAccountUseCase = verifyAccountUseCase;
        this.sendTransactionUseCase = sendTransactionUseCase;
        this.getExchangeRateUseCase = getExchangeRateUseCase;
    }

    public LiveData<Boolean> isLoading() { return isLoading; }
    public LiveData<String> getRecipientError() { return recipientError; }
    public LiveData<String> getAmountError() { return amountError; }
    public LiveData<String> getVerifiedRecipient() { return verifiedRecipient; }
    public LiveData<Result<Map<String, Object>>> getTransactionResult() { return transactionResult; }
    public LiveData<Boolean> isSendButtonEnabled() { return isSendButtonEnabled; }
    public LiveData<String> getExchangeRate() { return exchangeRate; }

    public void fetchExchangeRate() {
        getExchangeRateUseCase.execute(result -> {
            if (result instanceof Result.Success) {
                exchangeRate.postValue(((Result.Success<String>) result).data);
            } else if (result instanceof Result.Error) {
                exchangeRate.postValue(((Result.Error<String>) result).message);
            }
        });
    }

    public void onInputChanged(String recipientId, String amountStr, double currentBalance) {
        debounceHandler.removeCallbacks(debounceRunnable);
        debounceRunnable = () -> validateInputs(recipientId, amountStr, currentBalance);
        debounceHandler.postDelayed(debounceRunnable, 300);
    }

    private void validateInputs(String recipientId, String amountStr, double currentBalance) {
        boolean isRecipientValid = recipientId != null && recipientId.matches("^0\\.0\\.[0-9]+$");
        boolean isAmountValid = false;

        if (recipientId == null || recipientId.isEmpty()) {
             recipientError.postValue(null);
        } else if (!isRecipientValid) {
            recipientError.postValue("Valid Account ID is required.");
        } else {
            recipientError.postValue(null);
        }

        try {
            double amount = Double.parseDouble(amountStr);
            if (amount > 0 && amount <= currentBalance) {
                isAmountValid = true;
                amountError.postValue(null);
            } else if (amount > currentBalance) {
                amountError.postValue("Amount exceeds balance.");
            } else {
                amountError.postValue("Amount must be positive.");
            }
        } catch (NumberFormatException e) {
            if (amountStr != null && !amountStr.isEmpty()) {
                amountError.postValue("Invalid amount format.");
            } else {
                 amountError.postValue(null);
            }
        }
        isSendButtonEnabled.postValue(isRecipientValid && isAmountValid);
    }

    public void verifyAccountId(String accountId) {
        verifyAccountUseCase.execute(accountId, result -> {
            if (result instanceof Result.Loading) {
                isLoading.postValue(true);
            } else if (result instanceof Result.Success) {
                isLoading.postValue(false);
                if (((Result.Success<Boolean>) result).data) {
                    verifiedRecipient.postValue(accountId);
                } else {
                    recipientError.postValue("Invalid Account ID");
                }
            } else if (result instanceof Result.Error) {
                isLoading.postValue(false);
                recipientError.postValue(((Result.Error<Boolean>) result).message);
            }
        });
    }

    public void sendTransaction(String recipientId, String amountStr, String memo, double currentBalance) {
        sendTransactionUseCase.execute(recipientId, amountStr, memo, currentBalance, result -> {
            if (result instanceof Result.Loading) {
                isLoading.postValue(true);
            } else if (result instanceof Result.Success) {
                isLoading.postValue(false);
                saveTransactionToHistory(amountStr, recipientId, memo);
                transactionResult.postValue(result);
            } else if (result instanceof Result.Error) {
                isLoading.postValue(false);
                transactionResult.postValue(result);
            }
        });
    }

    private void saveTransactionToHistory(String amount, String receiverId, String memo) {
        String currentDate = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
        Transaction transaction = new Transaction();
        transaction.type = "Sent";
        transaction.amount = "-" + amount + " ℏ";
        transaction.party = receiverId;
        transaction.date = currentDate;
        transaction.status = "Completed";
        transaction.memo = memo;
        WalletStorage.saveTransaction(getApplication(), transaction);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        debounceHandler.removeCallbacks(debounceRunnable);
    }
}
