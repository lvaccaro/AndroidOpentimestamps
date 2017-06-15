package com.eternitywall.opentimestamps.activities;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.eternitywall.opentimestamps.IOUtil;
import com.eternitywall.opentimestamps.R;
import com.eternitywall.opentimestamps.adapters.FolderAdapter;
import com.eternitywall.opentimestamps.adapters.ItemAdapter;
import com.eternitywall.opentimestamps.dbs.DBHelper;
import com.eternitywall.opentimestamps.dbs.FolderDBHelper;
import com.eternitywall.opentimestamps.dbs.TimestampDBHelper;
import com.eternitywall.opentimestamps.models.Folder;
import com.eternitywall.ots.DetachedTimestampFile;
import com.eternitywall.ots.Hash;
import com.eternitywall.ots.OpenTimestamps;
import com.eternitywall.ots.StreamSerializationContext;
import com.eternitywall.ots.Timestamp;
import com.eternitywall.ots.Utils;
import com.eternitywall.ots.op.OpSHA256;
import com.sromku.simple.storage.SimpleStorage;
import com.sromku.simple.storage.Storage;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity implements FolderAdapter.OnItemClickListener {

    final int PERMISSION_EXTERNAL_STORAGE=100;
    Storage storage;
    FolderDBHelper dbHelper;
    TimestampDBHelper timestampDBHelper;

    private RecyclerView mRecyclerView;
    private FolderAdapter mAdapter;
    private List<Folder> mFolders;
    private RecyclerView.LayoutManager mLayoutManager;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = (TextView) findViewById(R.id.tvStatus);
        mRecyclerView = (RecyclerView) findViewById(R.id.my_recycler_view);

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        mRecyclerView.setHasFixedSize(true);

        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);

        DividerItemDecoration horizontalDecoration = new DividerItemDecoration(mRecyclerView.getContext(),
                DividerItemDecoration.VERTICAL);
        Drawable horizontalDivider = ContextCompat.getDrawable(this, R.drawable.divider_grey);
        horizontalDecoration.setDrawable(horizontalDivider);
        mRecyclerView.addItemDecoration(horizontalDecoration);

        // check permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPermission();
        }
        else {
            init();
        }
    }


    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {//Can add more as per requirement

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_EXTERNAL_STORAGE);

        } else {
            init();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults)
    {
        switch (requestCode) {
            case PERMISSION_EXTERNAL_STORAGE: {

                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED)     {
                    init();
                } else {

                    checkPermission();
                }
                return;
            }
        }
    }

    private void clearDB(){
        if (dbHelper == null) {
            dbHelper = new FolderDBHelper(this);
        }
        if (timestampDBHelper == null){
            timestampDBHelper = new TimestampDBHelper(this);
        }
        dbHelper.clearAll();
        timestampDBHelper.clearAll();

    }
    private void initDB(){
        // FOLDER ROOT
        {
            Folder folder = new Folder();
            folder.name = "External Storage";
            folder.roodDir = ".";
            folder.id = dbHelper.create(folder);
            mFolders.add(folder);
        }

        {
            Folder folder = new Folder();
            folder.name = "Pictures";
            folder.roodDir = Environment.DIRECTORY_PICTURES;
            folder.id = dbHelper.create(folder);
            folder.getNestedFiles(storage);
            mFolders.add(folder);
        }

        {
            Folder folder = new Folder();
            folder.name = "Documents";
            folder.roodDir = Environment.DIRECTORY_DOCUMENTS;
            folder.id = dbHelper.create(folder);
            folder.getNestedFiles(storage);
            mFolders.add(folder);
        }
        {
            Folder folder = new Folder();
            folder.name = "Camera";
            folder.roodDir = Environment.DIRECTORY_DCIM;
            folder.id = dbHelper.create(folder);
            folder.getNestedFiles(storage);
            mFolders.add(folder);
        }

    }

    private void init(){
        // Boot time procedure
        if (SimpleStorage.isExternalStorageWritable()) {
            storage = SimpleStorage.getExternalStorage();
        }
        else {
            storage = SimpleStorage.getInternalStorage(this);
        }

        // Check DB
        dbHelper = new FolderDBHelper(this);
        //dbHelper.clearAll();
        mFolders = dbHelper.getAll();
        if (mFolders.size()==0){
            initDB();
        }
        timestampDBHelper = new TimestampDBHelper(this);
        //timestampDBHelper.clearAll();

        // Specify and fill the adapter
        mAdapter = new FolderAdapter(this, mFolders);
        mAdapter.setOnItemClickListener(this);
        mRecyclerView.setAdapter(mAdapter);

        for (Folder folder : mFolders){
            check(folder, false);
        }

    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_check:
                for (Folder folder : mFolders){
                    if(folder.enabled==true) {
                        check(folder, false);
                    }
                }
                return true;
            case R.id.action_clear:
                AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
                alert.setTitle("Warning")
                        .setMessage("Are you sure to reset all timestamp proof?")
                        .setPositiveButton(getResources().getString(android.R.string.yes), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                clearDB();
                                for (Folder folder : mFolders){
                                    if(folder.enabled==true) {
                                        check(folder, false);
                                    }
                                }
                            }
                        })
                        .setNegativeButton(getResources().getString(android.R.string.no), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        })
                        .show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onDetailClick(View view, int position, long id) {
        if (mFolders.get(position).ots.length == 0)
            return;
        String ots = IOUtil.bytesToHex(mFolders.get(position).ots);
        String url = "https://opentimestamps.org/info.html?ots=";
        url += ots;
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        startActivity(i);
    }

    @Override
    public void onCheckingClick(View view, int position, long id) {
        if(mFolders.get(position).enabled == true) {
            check(mFolders.get(position), true);
        }
    }

    @Override
    public void onEnableClick(View view, int position, long id) {
        mFolders.get(position).enabled = true;
        dbHelper.update(mFolders.get(position));

        check(mFolders.get(position),false);
    }

    @Override
    public void onDisableClick(View view, int position, long id) {
        mFolders.get(position).enabled = false;
        dbHelper.update(mFolders.get(position));
    }


    private void check(final Folder folder, final boolean runCallbackStamp){

        new AsyncTask<Void,Void,Boolean>() {

            @Override
            protected Boolean doInBackground(Void... params) {
                List<File> files = folder.getNestedNotSynchedFiles(storage);
                if(files.size()>0)
                    return false;
                else
                    return true;
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                folder.state = Folder.State.CHECKING;
                mAdapter.notifyDataSetChanged();
            }

            @Override
            protected void onPostExecute(Boolean isAllStamped) {
                super.onPostExecute(isAllStamped);

                if (folder.lastSync == 0)
                    folder.state = Folder.State.NOTHING;
                else if (isAllStamped)
                    folder.state = Folder.State.STAMPED;
                else
                    folder.state = Folder.State.NOTUPDATED;
                mAdapter.notifyDataSetChanged();

                if(runCallbackStamp) {
                    if (folder.lastSync == 0 && isAllStamped) {
                        AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
                        alert.setTitle("Warning")
                                .setMessage("No file to Timestamp")
                                .show();
                    } else if (!isAllStamped) {
                        stamp(folder);
                    }
                }
            }

        }.execute();
    }

    private void stamp(final Folder folder){

        new AsyncTask<Void,Integer,Boolean>() {

            @Override
            protected Boolean doInBackground(Void... params) {
                List<File> files = folder.getNestedNotSynchedFiles(storage);
                List<DetachedTimestampFile> fileTimestamps = new ArrayList<>();
                Log.d("LIST", files.toString());

                // Build hash digest list
                /*List<Hash> hashes = new ArrayList<>();
                for (File file : files) {
                    try {
                        Log.d("STAMP", "FILE: "+file.getName());
                        byte[] bytes = IOUtil.readFile(file);
                        Hash sha256 = new Hash(IOUtil.SHA256(bytes));
                        hashes.add(sha256);
                        Log.d("STAMP", "HASH: "+IOUtil.bytesToHex(sha256.getValue()));
                    } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                // Build detached file to timestamp from hash
                fileTimestamps = new ArrayList<>();
                for (Hash hash : hashes) {
                    fileTimestamps.add(DetachedTimestampFile.from(new OpSHA256(), hash));
                }
                */
                int countFiles = 0;
                for (File file : files) {
                    Log.d("STAMP", "FILE: "+file.getName());
                    try {
                        Hash sha256 = new Hash(IOUtil.readFileSHA256(file));
                        fileTimestamps.add(DetachedTimestampFile.from(new OpSHA256(), sha256));
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    }
                    countFiles++;
                    publishProgress(countFiles);
                }

                // Stamp the markled list
                Timestamp merkleTip = OpenTimestamps.makeMerkleTree(fileTimestamps);
                folder.hash = merkleTip.getDigest();
                Log.d("STAMP", "MERKLE: "+IOUtil.bytesToHex(folder.hash));
                //private static Timestamp create(Timestamp timestamp, List<String> calendarUrls, Integer m, HashMap<String,String> privateCalendarUrls) {
                folder.ots = OpenTimestamps.stamp(merkleTip, null, 0, null);
                Log.d("STAMP", "OTS: "+IOUtil.bytesToHex(folder.ots));

                // Stamp proof info
                String info = OpenTimestamps.info(folder.ots);
                Log.d("STAMP", "INFO: "+info);

                // Save the ots

                for (DetachedTimestampFile file : fileTimestamps) {
                    timestampDBHelper.addTimestamp(file.getTimestamp());
                }
                return true;
            }

            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                folder.state = Folder.State.STAMPING;
                mAdapter.notifyDataSetChanged();
            }

            @Override
            protected void onPostExecute(Boolean aBoolean) {
                super.onPostExecute(aBoolean);

                if (aBoolean==false)
                    return;
                folder.state = Folder.State.STAMPED;
                folder.lastSync = System.currentTimeMillis();
                dbHelper.update(folder);
                mAdapter.notifyDataSetChanged();
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                super.onProgressUpdate(values);
                folder.countFiles = values[0];
                mAdapter.notifyItemChanged(mFolders.indexOf(folder));

            }
        }.execute();
    }
}
