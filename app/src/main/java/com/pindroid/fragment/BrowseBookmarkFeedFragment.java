/*
 * PinDroid - http://code.google.com/p/PinDroid/
 *
 * Copyright (C) 2010 Matt Schmidt
 *
 * PinDroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation; either version 3 of the License,
 * or (at your option) any later version.
 *
 * PinDroid is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PinDroid; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA
 */

package com.pindroid.fragment;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;

import com.pindroid.Constants;
import com.pindroid.Constants.BookmarkViewType;
import com.pindroid.R;
import com.pindroid.client.PinboardFeedClient;
import com.pindroid.fragment.BrowseBookmarksFragment.OnBookmarkSelectedListener;
import com.pindroid.model.FeedBookmark;
import com.pindroid.providers.BookmarkContent.Bookmark;
import com.pindroid.ui.BookmarkFeedAdapter;
import com.pindroid.util.AccountHelper;
import com.pindroid.util.SettingsHelper;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.ItemClick;

import java.util.List;

@EFragment
public class BrowseBookmarkFeedFragment extends ListFragment 
	implements LoaderManager.LoaderCallbacks<List<FeedBookmark>>, BookmarkBrowser, PindroidFragment  {
	
	@Bean BookmarkFeedAdapter adapter;
	
	@InstanceState String username = null;
	@InstanceState String tagname = null;
	@InstanceState String feed = null;
	
	FeedBookmark lastSelected = null;
	ListView lv;
	
	private BrowseBookmarksFragment.OnBookmarkSelectedListener bookmarkSelectedListener;
	
	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setRetainInstance(false);
	}
	
	@AfterViews
	public void init(){
		
		setListAdapter(adapter);

		if(username != null) {
			setListShown(false);
			
	    	getLoaderManager().initLoader(0, null, this);
	    	
			lv = getListView();
			lv.setTextFilterEnabled(true);
			lv.setFastScrollEnabled(true);

			lv.setItemsCanFocus(false);
			lv.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

			lv.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {
				public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
					menu.setHeaderTitle("Actions");
					MenuInflater inflater = getActivity().getMenuInflater();
					
					inflater.inflate(R.menu.browse_bookmark_context_menu_other, menu);
				}
			});
		}
	}

	@ItemClick
	void listItemClicked(FeedBookmark feedBookmark) {
		lastSelected = feedBookmark;

		String defaultAction = SettingsHelper.getDefaultAction(getActivity());

		if(defaultAction.equals("view")) {
			viewBookmark(lastSelected.toBookmark());
		} else if(defaultAction.equals("read")) {
			readBookmark(lastSelected.toBookmark());
		} else if(defaultAction.equals("edit")){
			addBookmark(lastSelected.toBookmark());
		} else {
			openBookmarkInBrowser(lastSelected.toBookmark());
		}
	}

	public void setQuery(String username, String tagname, String feed){
		this.username = username;
		this.tagname = tagname;
		this.feed = feed;
	}
	
	public void setUsername(String username){
		this.username = username;
	}
	
	public void refresh(){
		try{
			getLoaderManager().restartLoader(0, null, this);
		} catch(Exception e){}
	}
	
	@Override
	public void onResume(){
		super.onResume();
		
		if(Intent.ACTION_SEARCH.equals(getActivity().getIntent().getAction())) {
			String query = getActivity().getIntent().getStringExtra(SearchManager.QUERY);
			getActivity().setTitle(getString(R.string.search_results_global_tag_title, query));
		} else if(feed != null && feed.equals("recent")) {
			getActivity().setTitle(getString(R.string.browse_recent_bookmarks_title));
		} else if(feed != null && feed.equals("popular")) {
			getActivity().setTitle(getString(R.string.browse_popular_bookmarks_title));
		} else if(feed != null && feed.equals("network")) {
			getActivity().setTitle(getString(R.string.browse_network_bookmarks_title));
		} else {	
			if(tagname != null && tagname != "") {
				getActivity().setTitle(getString(R.string.browse_user_bookmarks_tagged_title, feed, tagname));
			} else {
				getActivity().setTitle(getString(R.string.browse_user_bookmarks_title, feed));
			}
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem aItem) {
		AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) aItem.getMenuInfo();
		final FeedBookmark feedBookmark = (FeedBookmark)lv.getItemAtPosition(menuInfo.position);
		
		switch (aItem.getItemId()) {
			case R.id.menu_bookmark_context_open:
				openBookmarkInBrowser(feedBookmark.toBookmark());
				return true;
			case R.id.menu_bookmark_context_view:				
				viewBookmark(feedBookmark.toBookmark());
				return true;
			case R.id.menu_bookmark_context_add:				
				addBookmark(feedBookmark.toBookmark());
				return true;
			case R.id.menu_bookmark_context_read:
				readBookmark(feedBookmark.toBookmark());
				return true;
			case R.id.menu_bookmark_context_share:
				bookmarkSelectedListener.onBookmarkShare(feedBookmark.toBookmark());
				return true;
		}
		return false;
	}
		
	private void openBookmarkInBrowser(Bookmark b) {
		bookmarkSelectedListener.onBookmarkSelected(b, BookmarkViewType.WEB);
	}
	
	private void viewBookmark(Bookmark b) {
		bookmarkSelectedListener.onBookmarkSelected(b, BookmarkViewType.VIEW);
	}
	
	private void readBookmark(Bookmark b){
		bookmarkSelectedListener.onBookmarkSelected(b, BookmarkViewType.READ);
	}
	
	private void addBookmark(Bookmark b){
		bookmarkSelectedListener.onBookmarkAdd(b);
	}

	public Loader<List<FeedBookmark>> onCreateLoader(int id, Bundle args) {
		if(Intent.ACTION_SEARCH.equals(getActivity().getIntent().getAction())) {
			String query = getActivity().getIntent().getStringExtra(SearchManager.QUERY);
			return new LoaderDrone(getActivity(), username, query, feed, AccountHelper.getAccount(username, getActivity()));
		} else {
			return new LoaderDrone(getActivity(), username, tagname, feed, AccountHelper.getAccount(username, getActivity()));
		}
	}
	
	public void onLoadFinished(Loader<List<FeedBookmark>> loader, List<FeedBookmark> data) {
	    adapter.setFeedBookmarks(data);

        if (isResumed()) {
            setListShown(true);
        } else {
            setListShownNoAnimation(true);
        }
	}
	
	public void onLoaderReset(Loader<List<FeedBookmark>> loader) {
	    adapter.setFeedBookmarks(null);
	}
	
	public static class LoaderDrone extends AsyncTaskLoader<List<FeedBookmark>> {
        
		private String user = "";
		private String tag = "";
		private String feed = "";
		private Account account = null;
		
        public LoaderDrone(Context context, String u, String t, String f, Account a) {
        	super(context);
        	
        	user = u;
            tag = t;
            feed = f;
            account = a;
        	
            onForceLoad();
        }

        @Override
        public List<FeedBookmark> loadInBackground() {
            List<FeedBookmark> results = null;

 		   try {
			   switch (feed) {
				   case "network":
					   String token = AccountManager.get(getContext()).getUserData(account, Constants.PREFS_SECRET_TOKEN);
					   results = PinboardFeedClient.get().getNetworkRecent(token, user);
					   break;
				   case "recent":
					   results = PinboardFeedClient.get().getRecent();
					   break;
				   case "popular":
					   results = PinboardFeedClient.get().getPopular();
					   break;
				   case "global":
					   results = PinboardFeedClient.get().searchGlobalTags(tag);
					   break;
				   default:
					   if(tag != null && !"".equals(tag)) {
						   results = PinboardFeedClient.get().getUserRecent(feed, tag);
					   } else {
						   results = PinboardFeedClient.get().getUserRecent(feed);
					   }
					   break;
			   }

 		   } catch (Exception e) {
 			   e.printStackTrace();
 		   }

           return results;
        }
    }
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		try {
			bookmarkSelectedListener = (OnBookmarkSelectedListener) activity;
		} catch (ClassCastException e) {
			throw new ClassCastException(activity.toString() + " must implement OnBookmarkSelectedListener");
		}
	}
}