package com.example.proyectoarpdm.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.proyectoarpdm.R;
import com.example.proyectoarpdm.models.ARModel;

import java.util.List;

public class ModelAdapter extends RecyclerView.Adapter<ModelAdapter.ViewHolder> {

    private final List<ARModel> models;
    private final OnModelClickListener listener;

    public interface OnModelClickListener {
        void onModelClick(ARModel model);
    }

    public ModelAdapter(List<ARModel> models, OnModelClickListener listener) {
        this.models = models;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_model, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ARModel model = models.get(position);
        holder.nameTextView.setText(model.getName());
        
        if (model.getImagen() != null && !model.getImagen().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(model.getImagen())
                    .fitCenter()
                    .placeholder(R.drawable.ic_launcher)
                    .error(R.drawable.ic_launcher)
                    .into(holder.modelImageView);
        } else {
            holder.modelImageView.setImageResource(R.drawable.ic_launcher);
        }

        holder.itemView.setOnClickListener(v -> listener.onModelClick(model));
    }

    @Override
    public int getItemCount() {
        return models.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView nameTextView;
        public ImageView modelImageView;

        public ViewHolder(View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.model_name);
            modelImageView = itemView.findViewById(R.id.model_image);
        }
    }
}
