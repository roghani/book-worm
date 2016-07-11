package com.ronakmanglani.booknerd.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.ronakmanglani.booknerd.BookNerdApp;
import com.ronakmanglani.booknerd.R;
import com.ronakmanglani.booknerd.activity.BookActivity;
import com.ronakmanglani.booknerd.activity.MainActivity;
import com.ronakmanglani.booknerd.adapter.BestsellerAdapter;
import com.ronakmanglani.booknerd.adapter.CategoryAdapter;
import com.ronakmanglani.booknerd.adapter.CategoryAdapter.OnCategoryClickListener;
import com.ronakmanglani.booknerd.model.Bestseller;
import com.ronakmanglani.booknerd.model.Book;
import com.ronakmanglani.booknerd.model.Category;
import com.ronakmanglani.booknerd.util.ApiUtil;
import com.ronakmanglani.booknerd.util.DimenUtil;
import com.ronakmanglani.booknerd.util.StringUtil;
import com.ronakmanglani.booknerd.util.VolleySingleton;
import com.ronakmanglani.booknerd.view.PaddingDecorationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;

import static com.ronakmanglani.booknerd.adapter.BestsellerAdapter.*;

public class BestsellerFragment extends Fragment implements OnBestsellerClickListener, OnCategoryClickListener {

    private Unbinder unbinder;

    private int currentState;
    private String listName;
    private BestsellerAdapter adapter;
    private GridLayoutManager layoutManager;

    @BindView(R.id.error_message)       View errorMessage;
    @BindView(R.id.progress_circle)     View progressCircle;
    @BindView(R.id.bestseller_list)     RecyclerView bestsellerList;
    @BindView(R.id.category_list)       RecyclerView categoryList;

    // Fragment lifecycle
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_bestseller, container, false);
        unbinder = ButterKnife.bind(this, v);

        // Setup category list
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        CategoryAdapter categoryAdapter = new CategoryAdapter(this);
        categoryList.setHasFixedSize(true);
        categoryList.setLayoutManager(layoutManager);
        categoryList.setAdapter(categoryAdapter);

        // Add padding decoration for bestseller list
        bestsellerList.addItemDecoration(new PaddingDecorationView(getContext(), R.dimen.recycler_item_padding));

        // Restore saved state, if exists
        if (savedInstanceState != null && savedInstanceState.containsKey(BookNerdApp.KEY_STATE)) {
            currentState = savedInstanceState.getInt(BookNerdApp.KEY_STATE);
            listName = savedInstanceState.getString(BookNerdApp.KEY_NAME);
            // Data had already been loaded: Display the data again
            if (currentState == BookNerdApp.STATE_LOADED) {
                ArrayList<Bestseller> bestsellerBooks = savedInstanceState.getParcelableArrayList(BookNerdApp.KEY_BESTSELLER);

                layoutManager = new GridLayoutManager(getContext(), DimenUtil.getNumberOfColumns(R.dimen.book_card_width, 1));
                adapter = new BestsellerAdapter(bestsellerBooks, this);

                bestsellerList.setHasFixedSize(true);
                bestsellerList.setLayoutManager(layoutManager);
                bestsellerList.setAdapter(adapter);

                onDownloadSuccessful();
            }
            // Data was still loading when fragment was lost: Load again
            else if (currentState == BookNerdApp.STATE_LOADING) {
                navigateToBestsellers();
            }
            // Data had failed to load when fragment was lost: Show error message again
            else if (currentState == BookNerdApp.STATE_FAILED) {
                onDownloadFailed();
            }
        }

        return v;
    }
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(BookNerdApp.KEY_STATE, currentState);
        outState.putString(BookNerdApp.KEY_NAME, listName);
        if (adapter != null) {
            outState.putParcelableArrayList(BookNerdApp.KEY_BESTSELLER, adapter.getList());
        }
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        VolleySingleton.getInstance().requestQueue.cancelAll(this.getClass().getName());
        unbinder.unbind();
    }

    // Navigation helpers
    public void navigateToBestsellers() {
        // Toggle Visibility
        categoryList.setVisibility(View.GONE);
        bestsellerList.setVisibility(View.GONE);
        errorMessage.setVisibility(View.GONE);
        progressCircle.setVisibility(View.VISIBLE);
        // Download data
        downloadBestsellerList();
    }
    public void navigateToCategories() {
        // Cancel any pending network requests
        VolleySingleton.getInstance().requestQueue.cancelAll(getClass().getName());
        // Reset objects/flags
        layoutManager = null;
        adapter = null;
        updateToolbarTitle(getString(R.string.drawer_bestseller));
        // Toggle Visibility
        errorMessage.setVisibility(View.GONE);
        progressCircle.setVisibility(View.GONE);
        bestsellerList.setVisibility(View.GONE);
        categoryList.setVisibility(View.VISIBLE);
    }
    public void updateToolbarTitle(String title) {
        ((DrawerFragment) getActivity()
                .getSupportFragmentManager()
                .findFragmentById(R.id.main_fragment))
                .toolbar.setTitle(title);
    }
    public boolean canGoBack() {
        return (adapter != null);
    }

    // JSON parsing and display
    private void downloadBestsellerList() {
        if (adapter == null) {
            adapter = new BestsellerAdapter(this);
            layoutManager = new GridLayoutManager(getContext(), DimenUtil.getNumberOfColumns(R.dimen.book_card_width, 1));
            bestsellerList.setHasFixedSize(true);
            bestsellerList.setLayoutManager(layoutManager);
            bestsellerList.setAdapter(adapter);
        }
        String urlToDownload = ApiUtil.getBestsellerList(listName);
        final JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, urlToDownload, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            JSONArray books = response.getJSONObject("results").getJSONArray("books");
                            for (int i = 0; i < books.length(); i++) {
                                JSONObject bookObject = books.getJSONObject(i);
                                String isbn10 = bookObject.getString("primary_isbn10");
                                String isbn13 = bookObject.getString("primary_isbn13");
                                String publisher = bookObject.getString("publisher");
                                String description = bookObject.getString("description");
                                String title = StringUtil.toTitleCase(bookObject.getString("title"));
                                String author = bookObject.getString("author");
                                String imageUrl = bookObject.getString("book_image");
                                String itemUrl = bookObject.getString("amazon_product_url");

                                adapter.addToList(new Bestseller(isbn10, isbn13, title,
                                        author, description, imageUrl, publisher, itemUrl));
                            }

                            onDownloadSuccessful();

                        } catch (Exception e) {
                            // JSON parsing error
                            onDownloadFailed();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // Network error
                        onDownloadFailed();
                    }
                });
        request.setTag(this.getClass().getName());
        request.setRetryPolicy(new DefaultRetryPolicy(10000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        VolleySingleton.getInstance().requestQueue.add(request);

        currentState = BookNerdApp.STATE_LOADING;
    }
    private void onDownloadSuccessful() {
        errorMessage.setVisibility(View.GONE);
        progressCircle.setVisibility(View.GONE);
        categoryList.setVisibility(View.GONE);
        bestsellerList.setVisibility(View.VISIBLE);
        adapter.notifyDataSetChanged();

        if (DimenUtil.isTablet()) {
            Book book = new Book(adapter.getList().get(0));
            ((MainActivity) getActivity()).loadDetailFragmentWith(book, false);
        }

        currentState = BookNerdApp.STATE_LOADED;
    }
    private void onDownloadFailed() {
        errorMessage.setVisibility(View.VISIBLE);
        progressCircle.setVisibility(View.GONE);
        bestsellerList.setVisibility(View.GONE);
        categoryList.setVisibility(View.GONE);

        currentState = BookNerdApp.STATE_FAILED;
    }

    // Click events
    @OnClick(R.id.try_again)
    public void onTryAgainClicked() {
        // Toggle Visibility
        errorMessage.setVisibility(View.GONE);
        bestsellerList.setVisibility(View.GONE);
        progressCircle.setVisibility(View.VISIBLE);
        // Try to download the data again
        adapter = null;
        downloadBestsellerList();
    }
    @Override
    public void onCategoryClicked(int position) {
        String displayName = Category.getCategoryList().get(position).getDisplayName();
        listName = Category.getCategoryList().get(position).getListName();
        updateToolbarTitle(displayName);
        navigateToBestsellers();
        if (DimenUtil.isTablet()) {
            ((MainActivity) getActivity()).loadDetailFragmentWith(null, false);
        }
    }
    @Override
    public void onBestsellerClicked(int position) {
        Book book = new Book(adapter.getList().get(position));
        if (DimenUtil.isTablet()) {
            ((MainActivity) getActivity()).loadDetailFragmentWith(book, false);
        } else {
            Intent intent = new Intent(getContext(), BookActivity.class);
            intent.putExtra(BookNerdApp.KEY_BOOK, book);
            startActivity(intent);
        }
    }
}

