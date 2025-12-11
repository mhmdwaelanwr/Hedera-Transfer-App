package anwar.mlsa.hadera.aou;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;

public class BlogAdapter extends RecyclerView.Adapter<BlogAdapter.ViewHolder> {
    private final ArrayList<Post> data;

    public BlogAdapter(ArrayList<Post> data) {
        this.data = data;
    }

    public void updateData(ArrayList<Post> newData) {
        data.clear();
        data.addAll(newData);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recylerview_offers_home, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(data.get(position));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView titleView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageview1);
            titleView = itemView.findViewById(R.id.textview1);
        }

        void bind(Post post) {
            titleView.setText(post.title);
            Glide.with(itemView.getContext()).load(post.image).into(imageView);
            itemView.setOnClickListener(v -> {
                VibrationManager.vibrate(itemView.getContext());
                Intent intent = new Intent(itemView.getContext(), BlogActivity.class);
                intent.putExtra("url", "https://mlsaegypt.org/blog/" + post.id);
                itemView.getContext().startActivity(intent);
            });
        }
    }
}