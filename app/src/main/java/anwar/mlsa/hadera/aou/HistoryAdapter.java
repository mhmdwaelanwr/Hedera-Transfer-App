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
            return new Gson().toJson(oldItem).equals(new Gson().toJson(newItem));
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
        holder.bind(getItem(position));
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

        void bind(Transaction transaction) {
            if (transaction == null) return;
            transactionType.setText(transaction.type);
            date.setText(transaction.date);
            amount.setText(transaction.amount);
            party.setText(transaction.party);
            status.setText(transaction.status);

            if (transaction.fee != null && !transaction.fee.isEmpty()) {
                fee.setText(transaction.fee);
                fee.setVisibility(View.VISIBLE);
            } else {
                fee.setVisibility(View.GONE);
            }

            if ("Sent".equalsIgnoreCase(transaction.type)) {
                amount.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.colorSent));
            } else {
                amount.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.colorReceived));
            }
            if ("SUCCESS".equalsIgnoreCase(transaction.status)) {
                status.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.colorReceived));
            } else {
                status.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.colorSent));
            }

            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(itemView.getContext(), TransactionDetailsActivity.class);
                intent.putExtra("transaction", new Gson().toJson(transaction));
                itemView.getContext().startActivity(intent);
            });
        }
    }
}
