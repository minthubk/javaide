package com.duy.ide;

import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.commonsware.cwac.pager.SimplePageDescriptor;
import com.duy.ide.autocomplete.AutoCompleteProvider;
import com.duy.ide.editor.BaseEditorActivity;
import com.duy.ide.editor.EditorFragment;
import com.duy.ide.editor.EditorPagerAdapter;
import com.duy.ide.file.FileManager;
import com.duy.ide.setting.JavaPreferences;

import java.io.File;

/**
 * Created by duy on 19/07/2017.
 */

public class PagePresenter implements EditPageContract.Presenter {

    private static final String TAG = "EditPresenter";
    private ViewPager mViewPager;
    private EditorPagerAdapter mPageAdapter;
    private TabLayout mTabLayout;
    private JavaPreferences mPreferences;
    private FileManager mFileManager;
    private BaseEditorActivity mContext;
    private Handler mHandler = new Handler();
    private AutoCompleteProvider autoCompleteProvider;

    public PagePresenter(BaseEditorActivity context, ViewPager mViewPager,
                         EditorPagerAdapter mPageAdapter, TabLayout tabLayout,
                         FileManager fileManager) {
        this.mViewPager = mViewPager;
        this.mPageAdapter = mPageAdapter;
        this.mTabLayout = tabLayout;
        this.mPreferences = new JavaPreferences(context);
        this.mFileManager = fileManager;
        this.mContext = context;
    }

    @Override
    public int gotoPage(File file) {
        if (file == null) return -1;
        return gotoPage(file.getPath());
    }

    @Override
    public int gotoPage(@NonNull String path) {
        int pos = mPageAdapter.getPositionForTag(path);
        if (pos != -1) {
            mViewPager.setCurrentItem(pos);
        }
        return pos;
    }

    @Override
    public void addPage(String path, boolean select) {
        try {
            File f = new File(path);
            if (f.exists()) {
                addPage(f, select);
            }
        } catch (Exception e) {
        }
    }

    @Override
    public void addPage(File file, boolean select) {
        if (file == null || !file.exists()) return;

        int position = mPageAdapter.getPositionForTag(file.getPath());
        if (position != -1) { //existed in list file
            //check need select tab
            if (select) {
                TabLayout.Tab tab = mTabLayout.getTabAt(position);
                if (tab != null) {
                    tab.select();
                    mViewPager.setCurrentItem(position);
                }
            }
        } else { //new file
            if (mPageAdapter.getCount() >= mPreferences.getMaxPage()) {
                Fragment existingFragment = mPageAdapter.getExistingFragment(0);
                if (existingFragment != null) {
                    mFileManager.removeTabFile(existingFragment.getTag());
                    removePage(0);
                }
            }

            //add to database
            mFileManager.addNewPath(file.getPath());

            //new page
            mPageAdapter.add(new SimplePageDescriptor(file.getPath(), file.getName()));
            invalidateTab();
            setAutoCompleteProvider(autoCompleteProvider);
            if (select) {
                int indexOfNewPage = mPageAdapter.getCount() - 1;
                TabLayout.Tab tab = mTabLayout.getTabAt(indexOfNewPage);
                if (tab != null) {
                    tab.select();
                    mViewPager.setCurrentItem(indexOfNewPage);
                }
            }
        }
    }

    @Override
    public void invalidateTab() {
        Log.d(TAG, "invalidateTab() called");
        LayoutInflater layoutInflater = LayoutInflater.from(mContext);
        mTabLayout.removeAllTabs();
        for (int i = 0; i < mPageAdapter.getCount(); i++) {

            View view = layoutInflater.inflate(R.layout.item_tab_file, null);
            view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            TabLayout.Tab tab = mTabLayout.newTab();
            mTabLayout.addTab(tab);
            tab.setCustomView(view);

            View close = view.findViewById(R.id.img_close);
            final int position = i;
            close.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    removePage(position);
                }
            });
            TextView txtTitle = view.findViewById(R.id.txt_title);
            txtTitle.setText(mPageAdapter.getPageTitle(i));
            txtTitle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mViewPager.setCurrentItem(position);
                }
            });

            if (i == mViewPager.getCurrentItem()) {
                tab.select();
            }
            final EditorFragment fm = mPageAdapter.getExistingFragment(position);
            if (fm != null && fm.getTag().endsWith(".java")) {
                View run = view.findViewById(R.id.image_run_file);
                run.setVisibility(View.VISIBLE);
                run.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mContext.runFile(fm.getTag());
                    }
                });
            }
        }

    }


    @Override
    public void removePage(String path) {
        removePage(mPageAdapter.getPositionForTag(path));
    }

    @Override
    public void removePage(int position) {
        Fragment existingFragment = mPageAdapter.getExistingFragment(position);
        if (existingFragment == null) {
            if (DLog.DEBUG) DLog.d(TAG, "removePage: " + "null page " + position);
            return;
        }

        //delete in database
        String filePath = existingFragment.getTag();
        mFileManager.removeTabFile(filePath);

        //remove page
        mPageAdapter.remove(position);
        invalidateTab();

        Toast.makeText(mContext,
                mContext.getString(R.string.closed) + " " + new File(filePath).getName(),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean hasPage(String path) {
        return mPageAdapter.getPositionForTag(path) > -1;
    }

    @Override
    public int getPagePosition(String path) {
        return mPageAdapter.getPositionForTag(path);
    }

    @Override
    public EditPageContract.View getCurrentPage() {
        return mPageAdapter.getCurrentFragment();
    }

    @Override
    public void showError(EditPageContract.View view, int line) {

    }

    @Override
    public void pause() {
        mPreferences.put(JavaPreferences.TAB_POSITION_FILE, mTabLayout.getSelectedTabPosition());
    }

    public void setAutoCompleteProvider(@NonNull AutoCompleteProvider autoCompleteProvider) {
        Log.d(TAG, "setAutoCompleteProvider() called with: autoCompleteProvider = [" + autoCompleteProvider + "]");
        if (this.autoCompleteProvider != null) {
            this.autoCompleteProvider.dispose();
        }
        this.autoCompleteProvider = autoCompleteProvider;
        for (int i = 0; i < mPageAdapter.getCount(); i++) {
            EditorFragment fm = mPageAdapter.getExistingFragment(i);
            if (fm != null) {
                fm.setAutoCompleteProvider(autoCompleteProvider);
            }
        }
    }
}
