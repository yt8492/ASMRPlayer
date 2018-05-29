package jp.zliandroid.asmrplayer;

import android.Manifest;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTabHost;
import android.support.v4.content.PermissionChecker;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TabHost;

public class WakeupActivity extends FragmentActivity implements NavigationView.OnNavigationItemSelectedListener,
        PlayListFragment.OnFragmentInteractionListener,AlbumListFragment.OnFragmentInteractionListener,FilerFragment.OnFragmentInteractionListener {

    private static final String TAG = "Storage Permission";
    private int REQUEST_CODE_STORAGE_PERMISSION = 0x01;

    FragmentTabHost tabHost = null;
    FragmentManager mFragmentManager = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wakeup);

        if(PermissionChecker.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)!=
                PackageManager.PERMISSION_GRANTED){
            requestStoragePermission();
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        //FragmentManagerの取得
        mFragmentManager = getSupportFragmentManager();
        //xmlからFragmentTabHostを取得、idが android.R.id.tabhost である点に注意
        tabHost = (FragmentTabHost)findViewById(android.R.id.tabhost);
        //ContextとFragmentManagerと、FragmentがあたるViewのidを渡してセットアップ
        tabHost.setup(this, mFragmentManager, R.id.content);
        //String型の引数には任意のidを渡す
        //今回は2つのFragmentをFragmentTabHostから切り替えるため、2つのTabSpecを用意する
        TabHost.TabSpec mTabSpec1 = tabHost.newTabSpec("tab_playlist");
        TabHost.TabSpec mTabSpec2 = tabHost.newTabSpec("tab_album");
        TabHost.TabSpec mTabSpec3 = tabHost.newTabSpec("tab_folder");


        //Tab上に表示する文字を渡す
        mTabSpec1.setIndicator("プレイリスト");
        mTabSpec2.setIndicator("アルバム");
        mTabSpec3.setIndicator("フォルダ");

        Bundle args = new Bundle();
        args.putString("string", "message");

        //それぞれのTabSpecにclassを対応付けるように引数を渡す
        //第3引数はBundleを持たせることで、Fragmentに値を渡せる。不要である場合はnullを渡す
        tabHost.addTab(mTabSpec1, PlayListFragment.class, null);
        tabHost.addTab(mTabSpec2, AlbumListFragment.class, null);
        tabHost.addTab(mTabSpec3, FilerFragment.class, null);


    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.wakeup, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_playlist) {
            tabHost.setCurrentTabByTag("tab_playlist");
        } else if (id == R.id.nav_album) {
            tabHost.setCurrentTabByTag("tab_album");
        } else if (id == R.id.nav_folder) {
            tabHost.setCurrentTabByTag("tab_folder");
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onFragmentInteraction(Uri uri) {
    }

    private void requestStoragePermission(){
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

            Log.d(TAG, "shouldShowRequestPermissionRationale:追加説明");
            // 権限チェックした結果、持っていない場合はダイアログを出す
            new AlertDialog.Builder(this)
                    .setTitle("パーミッションの追加説明")
                    .setMessage("このアプリで音声ファイルを再生するにはパーミッションが必要です")
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(WakeupActivity.this,
                                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                    REQUEST_CODE_STORAGE_PERMISSION);
                        }
                    })
                    .create()
                    .show();
            return;
        }

        // 権限を取得する
        ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                REQUEST_CODE_STORAGE_PERMISSION);
        return;
    }
}
