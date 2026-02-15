package anwar.mlsa.hadera.aou;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;

import java.util.Objects;

public class HistoryAdapter extends ListAdapter<Transaction, HistoryAdapter.ViewHolder> {

    private final Gson gson = new Gson();

    public HistoryAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<Transaction> DIFF_CALLBACK = new DiffUtil.ItemCallback<Transaction>() {
        @Override
        public boolean areItemsTheSame(@NonNull Transaction oldItem, @NonNull Transaction newItem) {
            return Objects.equals(oldItem.transactionId, newItem.transactionId);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Transaction oldItem, @NonNull Transaction newItem) {
            return Objects.equals(oldItem.type, newItem.type) &&
                   Objects.equals(oldItem.amount, newItem.amount) &&
                   Objects.equals(oldItem.party, newItem.party) &&
                   Objects.equals(oldItem.date, newItem.date) &&
                   Objects.equals(oldItem.status, newItem.status) &&
                   Objects.equals(oldItem.fee, newItem.fee);
        }
    };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recyclerview_history_home, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), gson);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView transactionType, date, amount, party, status, fee;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            transactionType = itemView.findViewById(R.id.textview1);
            date = itemView.findViewById(R.id.textview2);
            amount = itemView.findViewById(R.id.textview3);
            party = itemView.findViewById(R.id.textview4);
            status = itemView.findViewById(R.id.textview5);
            fee = itemView.findViewById(R.id.fee);
        }

        void bind(Transaction transaction, Gson gson) {
            if (transaction == null) return;
            
            transactionType.setText(transaction.type != null ? transaction.type : "");
            date.setText(transaction.date != null ? transaction.date : "");
            amount.setText(transaction.amount != null ? transaction.amount : "0 HBAR");
            party.setText(transaction.party != null ? transaction.party : "");
            status.setText(transaction.status != null ? transaction.status : "");

            if (transaction.fee != null && !transaction.fee.isEmpty()) {
                fee.setText(transaction.fee);
                fee.setVisibility(View.VISIBLE);
            } else {
                fee.setVisibility(View.GONE);
            }

            // UI Enhancement: Conditional Colors
            int contextColor = ContextCompat.getColor(itemView.getContext(), android.R.color.black);
            if ("Sent".equalsIgnoreCase(transaction.type)) {
                contextColor = ContextCompat.getColor(itemView.getContext(), R.color.colorSent);
            } else if ("Received".equalsIgnoreCase(transaction.type)) {
                contextColor = ContextCompat.getColor(itemView.getContext(), R.color.colorReceived);
            }
            amount.setTextColor(contextColor);

            if ("SUCCESS".equalsIgnoreCase(transaction.status)) {
                status.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.colorReceived));
            } else {
                status.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.colorSent));
            }

            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(itemView.getContext(), TransactionDetailsActivity.class);
                intent.putExtra("transaction", gson.toJson(transaction));
                itemView.getContext().startActivity(intent);
            });
        }
    }
}
