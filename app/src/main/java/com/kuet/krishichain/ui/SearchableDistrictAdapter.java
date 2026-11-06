package com.kuet.krishichain.ui;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Custom ArrayAdapter for AutoCompleteTextView ensuring full 64-district browsing & substring typing filter. */
public class SearchableDistrictAdapter extends ArrayAdapter<String> {
    private final List<String> fullList;
    private List<String> filteredList;

    public SearchableDistrictAdapter(@NonNull Context context, int resource, @NonNull List<String> objects) {
        super(context, resource, new ArrayList<>(objects));
        this.fullList = new ArrayList<>(objects);
        this.filteredList = new ArrayList<>(objects);
    }

    @Override
    public int getCount() {
        return filteredList.size();
    }

    @Override
    public String getItem(int position) {
        if (position >= 0 && position < filteredList.size()) {
            return filteredList.get(position);
        }
        return "";
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                if (constraint == null || constraint.length() == 0) {
                    results.values = new ArrayList<>(fullList);
                    results.count = fullList.size();
                } else {
                    String query = constraint.toString().toLowerCase(Locale.ROOT).trim();
                    List<String> matched = new ArrayList<>();
                    for (String item : fullList) {
                        if (item.toLowerCase(Locale.ROOT).contains(query)) {
                            matched.add(item);
                        }
                    }
                    results.values = matched;
                    results.count = matched.size();
                }
                return results;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results != null && results.values != null) {
                    filteredList = (List<String>) results.values;
                } else {
                    filteredList = new ArrayList<>(fullList);
                }
                clear();
                addAll(filteredList);
                notifyDataSetChanged();
            }

            @Override
            public CharSequence convertResultToString(Object resultValue) {
                return resultValue == null ? "" : resultValue.toString();
            }
        };
    }
}
