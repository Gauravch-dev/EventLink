package com.example.eventlink;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.List;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    private Context context;
    private List<DocumentSnapshot> eventList;
    private List<Double> distances; // ✅ Correct name
    private String userId, userEmail;

    public EventAdapter(Context context,
                        List<DocumentSnapshot> eventList,
                        List<Double> distances,
                        String userId,
                        String userEmail) {

        this.context = context;
        this.eventList = eventList;
        this.distances = distances;
        this.userId = userId;
        this.userEmail = userEmail;
    }

    // ✅ SINGLE ViewHolder (not duplicated)
    static class EventViewHolder extends RecyclerView.ViewHolder {

        TextView eventName, eventDomain, eventLocation, eventDistance;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);

            eventName = itemView.findViewById(R.id.eventName);
            eventDomain = itemView.findViewById(R.id.eventDomain);
            eventLocation = itemView.findViewById(R.id.eventLocation);
            eventDistance = itemView.findViewById(R.id.eventDistance);
        }
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(context).inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(view);
    }


    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {

        DocumentSnapshot doc = eventList.get(position);

        String name = doc.getString("name");
        String domain = doc.getString("domain");
        String address = doc.getString("address");
        double distance = distances.get(position); // ✅ FIXED

        holder.eventName.setText(name);
        holder.eventDomain.setText(domain);
        holder.eventLocation.setText(address);
        holder.eventDistance.setText(String.format("%.2f km away", distance));

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, EventDescActivity.class);

            intent.putExtra("eventName", name);
            intent.putExtra("eventCategory", domain);
            intent.putExtra("eventDate", doc.getString("date"));
            intent.putExtra("eventImage", doc.getString("imageUrl"));
            intent.putExtra("eventDescription", doc.getString("description"));
            intent.putExtra("eventLocation", address);
            intent.putExtra("userId", userId);
            intent.putExtra("userEmail", userEmail);

            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return eventList.size();
    }
}
