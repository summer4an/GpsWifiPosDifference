
/*

気圧計の値の取得
  http://www.adakoda.com/adakoda/2011/12/android-galaxy-nexus-hpa.html

現在の気圧
  http://www.aor.co.jp/live-weather/Past_Month.html
  http://www.jma.go.jp/jp/amedas_h/

各センサーの単位
  http://blog.goo.ne.jp/marunomarunogoo/e/91687f6fac91a184ae078f743b9262c8

javaの連想配列(ハッシュマップ)
  http://www.javadrive.jp/start/hashmap/index2.html

電池に関して取得
  http://www.adakoda.com/android/000140.html
  http://goodroid.fc2-rentalserver.com/index.php?mode=public&action=techno&pid=94

GPSに関して取得
  http://www.adakoda.com/android/000125.html

  GPSから取るかWifiから取るかを自動選択するgetBestProviderというAPIがあるようだが、イマイチっぽい。
    http://thinkit.co.jp/article/1221/1?page=0,1
    http://andante.in/i/android%E3%82%A2%E3%83%97%E3%83%AAtips/%E4%BD%8D%E7%BD%AE%E6%83%85%E5%A0%B1%E3%81%AE%E3%83%97%E3%83%AD%E3%83%90%E3%82%A4%E3%83%80%E9%81%B8%E5%AE%9A/

  デバッグ用コンパイルと本番用コンパイルでAPI keyを自動的に切り替える。
    http://espresso3389.hatenablog.com/entry/20111229/1325096705

*/

package info.nakajimadevnakajima.GpsWifiPosDifference;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.OverlayItem;
import com.google.android.maps.Projection;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;


public class GpsWifiPosDifferenceActivity extends MapActivity {
    GpsInfoClass locationGps=null;
    GpsInfoClass locationWifi=null;

    private MapView mapView=null;
    private MapController mapController=null;
    List<Overlay> mapOverlays=null;
    Drawable drawableGPS=null;
    Drawable drawableWifi=null;
    MyItemizedOverlay itemizedoverlayGPS=null;
    MyItemizedOverlay itemizedoverlayWifi=null;
    DrawOverlay drawOverlay=null;

    int[] posAllLatE6 = new int[2];
    int[] posAllLonE6 = new int[2];

    private class GpsInfoClass implements LocationListener{
        private int movedToNowPositionKaisuu=0; //マップの表示範囲を現在の位置に設定したかどうかのフラグ。
                                                    //毎回表示範囲を切り替えるのはいまいちなので、はじめの1回だけ行う。
                                                    //0:まだ一度も移動していない 1:1つのセンサーだけ取れて移動した後 2:2つとも取れて移動した後
        public int latE6=0;
        public int lonE6=0;
        public int altE6=0;

        int provider; //0でGPS、1でwifi
        private LocationManager mLocationManager;
        TextView textViewLocationInformation;
        TextView textViewLocationDiff;

        public GpsInfoClass(int provider){
            this.provider = provider;

            mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            textViewLocationDiff = (TextView)findViewById(R.id.textViewLocationDiff);

            if(provider==0){ //GPSの場合
                textViewLocationInformation = (TextView)findViewById(R.id.textViewLocationGpsInformation);
            }
            else{ //Wifiの場合
                textViewLocationInformation = (TextView)findViewById(R.id.textViewLocationWifiInformation);
            }
            textViewLocationInformation.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if(latE6!=0 && lonE6!=0){
                                mapController.animateTo(   new GeoPoint(latE6, lonE6)   );
                            }
                        }
                    });

            if(mLocationManager==null){
                textViewLocationInformation.setVisibility(View.INVISIBLE);
                textViewLocationInformation.setEnabled(false);
            }
        }

        public void requestLocationUpdates() {
            if (mLocationManager != null) {
                if(provider==0){ //GPSの場合
                    mLocationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER, //GPSで取得する場合
                            0, 0, this);
                }
                else{ //Wifiの場合
                    mLocationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER, //Wifi等から取得する場合
                            0, 0, this);
                }
            }
        }

        public void removeUpdates() {
            if (mLocationManager != null) {
                mLocationManager.removeUpdates(this);
            }
        }

        //private int b=0;
        @Override
        public void onLocationChanged(Location location) {
            /*
            { //テスト用コード
                location.setLatitude(location.getLatitude()+b*0.00001);
                location.setLongitude(location.getLongitude()+b*0.00001);
                b++;
            } //テスト用コード
            */
            /*
            { //テスト用コード
                if(provider==0){location.setLatitude(40714288); location.setLongitude(-74006036);}
                else           {location.setLatitude(40714497); location.setLongitude(-74006321);}
            } //テスト用コード
            */

            latE6=(int)(location.getLatitude() *1000000);
            lonE6=(int)(location.getLongitude()*1000000);
            altE6=(int)(location.getAltitude() *1000000);

            posAllLatE6[provider]=latE6;
            posAllLonE6[provider]=lonE6;

            { //テキストで表示。
                String text;

                if(provider==0){ //GPSの場合
                    text="○Location (GPS)\n";
                }
                else{ //Wifiの場合
                    text="○Location (Wifi)\n";
                }

                text += "Lat:"+String.format("%11.7f",latE6*0.000001)+"\n";
                text += "Lon:"+String.format("%11.7f",lonE6*0.000001)+"\n";
                text += "Alt:"+String.format("%11.7f",altE6*0.000001)+"\n";
                text += "Accuracy:"+String.valueOf(location.getAccuracy())+"\n";
                text += "Time:"+String.valueOf(location.getTime())+"\n";
                text += "Speed:"+String.valueOf(location.getSpeed())+"\n";
                text += "Bearing:"+String.valueOf(location.getBearing())+"\n";

                textViewLocationInformation.setText(text);
            } //テキストで表示。

            { //マップの更新。
                if(provider==0){ //GPS
                    if(itemizedoverlayGPS!=null){
                        mapOverlays.remove(itemizedoverlayGPS);
                        itemizedoverlayGPS=null;
                    }

                    itemizedoverlayGPS = new MyItemizedOverlay(drawableGPS, GpsWifiPosDifferenceActivity.this);

                    GeoPoint point2 = new GeoPoint(latE6, lonE6);
                    OverlayItem overlayitem2 = new OverlayItem(point2, "GPS", "this is GPS");
                    itemizedoverlayGPS.addOverlay(overlayitem2);

                    mapOverlays.add(itemizedoverlayGPS);
                }
                else{ //Wifi
                    if(itemizedoverlayWifi!=null){
                        mapOverlays.remove(itemizedoverlayWifi);
                        itemizedoverlayWifi=null;
                    }

                    itemizedoverlayWifi = new MyItemizedOverlay(drawableWifi, GpsWifiPosDifferenceActivity.this);

                    GeoPoint point2 = new GeoPoint(latE6, lonE6);
                    OverlayItem overlayitem2 = new OverlayItem(point2, "Wifi", "this is Wifi");
                    itemizedoverlayWifi.addOverlay(overlayitem2);

                    mapOverlays.add(itemizedoverlayWifi);
                }

                { //線や円を描く。
                    if(drawOverlay==null){
                        drawOverlay = new DrawOverlay();
                        mapView.getOverlays().add(drawOverlay);
                    }

                    if(2<=movedToNowPositionKaisuu){ //GPSとWifiの間に線を引く。
                        drawOverlay.setBetweenLine(
                                new GeoPoint(posAllLatE6[0], posAllLonE6[0]),
                                new GeoPoint(posAllLatE6[1], posAllLonE6[1])
                                );
                    } //GPSとWifiの間に線を引く。

                    drawOverlay.setPositionCircle(
                            provider,
                            new GeoPoint(latE6, lonE6),
                            location.getAccuracy());
                } //線や円を描く。

                { //マップの移動。
                    if(movedToNowPositionKaisuu==0){
                        movedToNowPositionKaisuu=1;
                        mapController.setZoom(21);
                        mapController.animateTo(   new GeoPoint(latE6, lonE6)   );
                    }
                    else if(movedToNowPositionKaisuu==1 &&
                            posAllLatE6[0]!=0 && posAllLonE6[0]!=0 &&
                            posAllLatE6[1]!=0 && posAllLonE6[1]!=0
                        ){
                        movedToNowPositionKaisuu=2;
                        mapController.zoomToSpan(
                                (int)(Math.abs(posAllLatE6[0]-posAllLatE6[1])*1.2),
                                (int)(Math.abs(posAllLonE6[0]-posAllLonE6[1])*1.2) ); //少し広めに表示。
                        mapController.animateTo(   new GeoPoint(
                                (posAllLatE6[0]+posAllLatE6[1])/2,
                                (posAllLonE6[0]+posAllLonE6[1])/2   )); //2点の中心地点。
                    }
                } //マップの移動。

                mapView.invalidate();
            } //マップの更新。

            { //GPSとWifiの距離の差をテキストで表示。
                if(locationGps!=null && locationWifi!=null &&
                        locationGps.latE6!=0 && locationGps.lonE6!=0 &&
                        locationWifi.latE6!=0 && locationWifi.lonE6!=0
                        ){
                    double latdiff=(locationGps.latE6-locationWifi.latE6)*0.000001;
                    double londiff=(locationGps.lonE6-locationWifi.lonE6)*0.000001;
                    double altdiff=0;//(locationGps.altE6-locationWifi.altE6)*0.000001;
                    double diff=Math.sqrt(latdiff*latdiff + altdiff*altdiff + londiff*londiff)*40075.0*1000.0/360.0;
                    textViewLocationDiff.setText("Diff GPS and Wifi="+String.format("%9.5fm\n", diff));
                }
            } //GPSとWifiの距離の差をテキストで表示。
        }

        @Override
        public void onProviderDisabled(String provider) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            /*
            switch (status) {
            case LocationProvider.AVAILABLE:
                Log.d("Status", "AVAILABLE");
                break;
            case LocationProvider.OUT_OF_SERVICE:
                Log.d("Status", "OUT_OF_SERVICE");
                break;
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                Log.d("Status", "TEMPORARILY_UNAVAILABLE");
                break;
            }
            */
        }
    }


    public class DrawOverlay extends Overlay {
        private final int NOWPOSITION_KEEP_NUM=1000; //センサーから取得した位置情報を保持しておく個数。

        Paint paintRed;
        Paint[] paints=new Paint[2];

        private GeoPoint lineBetweenStart=null;
        private GeoPoint lineBetweenEnd=null;

        private GeoPoint[] circleCenter = new GeoPoint[2];
        private float[] circleRadius = new float[2];

        ArrayList<GeoPoint> positionCenters0 = new ArrayList<GeoPoint>();
        ArrayList<GeoPoint> positionCenters1 = new ArrayList<GeoPoint>();

        public DrawOverlay() {
            paintRed = new Paint(Paint.ANTI_ALIAS_FLAG);
            paintRed.setStyle(Paint.Style.STROKE);
            paintRed.setAntiAlias(true);
            paintRed.setStrokeWidth(3);
            paintRed.setColor(Color.RED);
            for(int i=0 ; i<2 ; i++){
                paints[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
                paints[i].setStyle(Paint.Style.STROKE);
                paints[i].setAntiAlias(true);
                paints[i].setStrokeWidth(3);

                //線を描くときに設定するので、ここで色設定をしても意味がない。
                //if(i==0){
                //    paints[i].setColor(Color.GREEN);
                //}else{
                //    paints[i].setColor(Color.BLUE);
                //}
            }

            this.lineBetweenStart = null;
            this.lineBetweenEnd = null;

            for(int i=0 ; i<2 ; i++){
                circleCenter[i]=null;
                circleRadius[i]=0;
            }

            TextView textViewLocationDiff = (TextView)findViewById(R.id.textViewLocationDiff);
            textViewLocationDiff.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if(lineBetweenStart!=null && lineBetweenEnd!=null){
                                int lat_center = (lineBetweenStart.getLatitudeE6() + lineBetweenEnd.getLatitudeE6()) / 2;
                                int lon_center = (lineBetweenStart.getLongitudeE6() + lineBetweenEnd.getLongitudeE6()) / 2;

                                mapController.animateTo( new GeoPoint(lat_center, lon_center) );
                            }
                        }
                    });
        }

        public void setBetweenLine(GeoPoint lineBetweenGPS, GeoPoint lineBetweenWifi) {
            this.lineBetweenStart = lineBetweenGPS;
            this.lineBetweenEnd = lineBetweenWifi;
        }

        public void setPositionCircle(int provider, GeoPoint circleCenter, float circleRadius){
            this.circleCenter[provider] = circleCenter;
            this.circleRadius[provider] = circleRadius;

            { //位置の履歴表示用の情報を記録。
                ArrayList<GeoPoint> positionCenterTemp;
                if(provider==0){
                    positionCenterTemp = positionCenters0;
                }
                else{
                    positionCenterTemp = positionCenters1;
                }

                positionCenterTemp.add(circleCenter);
                if(NOWPOSITION_KEEP_NUM < positionCenterTemp.size()){
                    positionCenterTemp.remove(0);
                }
            } //位置の履歴表示用の情報を記録。
        }

        @Override
        public void draw(Canvas canvas, MapView mapView, boolean shadow) {
            super.draw(canvas, mapView, shadow);

            if (!shadow) {
                //円を描く
                for(int i=0 ; i<2 ; i++){
                    if(0<circleRadius[i]){
                        Projection projection = mapView.getProjection();

                        Point pxCenter = projection.toPixels(circleCenter[i], null);
                        float circleRadiusPixels = projection.metersToEquatorPixels(circleRadius[i]);

                        { //円を描く
                            //塗りつぶしを描く
                            if(i==0){
                                paints[i].setARGB(30, 0, 255, 0); //Color.GREEN
                            }else{
                                paints[i].setARGB(30, 0, 0, 255); //Color.BLUE;
                            }
                            paints[i].setStyle(Paint.Style.FILL);
                            canvas.drawCircle(pxCenter.x, pxCenter.y, circleRadiusPixels, paints[i]);

                            //線を描く
                            if(i==0){
                                paints[i].setARGB(255, 0, 255, 0); //Color.GREEN
                            }else{
                                paints[i].setARGB(255, 0, 0, 255); //Color.BLUE;
                            }
                            paints[i].setStyle(Paint.Style.STROKE);
                            canvas.drawCircle(pxCenter.x, pxCenter.y, circleRadiusPixels, paints[i]);
                        } //円を描く
                    }
                }

                //2点を繋ぐ赤い線を描く
                if(lineBetweenStart!=null && lineBetweenEnd!=null){
                    Path path = new Path();
                    Projection projection = mapView.getProjection();
                    Point pxStart = projection.toPixels(lineBetweenStart, null);
                    Point pxEnd = projection.toPixels(lineBetweenEnd, null);
                    path.moveTo(pxStart.x, pxStart.y);
                    path.lineTo(pxEnd.x, pxEnd.y);

                    canvas.drawPath(path, paintRed);
                }

                { //過去の位置の履歴の線を引く。
                    for(int provider=0 ; provider<2 ; provider++){
                        ArrayList<GeoPoint> positionCenterTemp;
                        if(provider==0){
                            positionCenterTemp = positionCenters0;
                        }
                        else{
                            positionCenterTemp = positionCenters1;
                        }

                        if(positionCenterTemp.size()<2){
                            continue;
                        }

                        Path path = new Path();
                        Projection projection = mapView.getProjection();

                        Point pxStart = projection.toPixels(positionCenterTemp.get(0), null);
                        path.moveTo(pxStart.x, pxStart.y);

                        for(int i=1 ; i<positionCenterTemp.size() ; i++){
                            Point pxPoint = projection.toPixels(positionCenterTemp.get(i), null);
                            path.lineTo(pxPoint.x, pxPoint.y);
                        }

                        canvas.drawPath(path, paints[provider]);
                    }
                } //過去の位置の履歴の線を引く。

            }
        }
    }

    private class MyItemizedOverlay extends ItemizedOverlay<OverlayItem> {
        private ArrayList<OverlayItem> mOverlays = new ArrayList<OverlayItem>();
        private Context mContext;

        public MyItemizedOverlay(Drawable defaultMarker, Context context) {
            super(boundCenterBottom(defaultMarker));
            mContext = context;
        }

        public void addOverlay(OverlayItem overlay) {
            mOverlays.add(overlay);
            populate();
        }

        @Override
        protected OverlayItem createItem(int i) {
            return mOverlays.get(i);
        }

        @Override
        public int size() {
            return mOverlays.size();
        }

        @Override
        protected boolean onTap(int index) {
            OverlayItem item = mOverlays.get(index);
            AlertDialog.Builder dialog = new AlertDialog.Builder(mContext);
            dialog.setTitle(item.getTitle());
            dialog.setMessage(item.getSnippet());
            dialog.show();
            return true;
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        locationGps = new GpsInfoClass(0);
        locationWifi = new GpsInfoClass(1);

        { //MapViewの準備
            mapView = new MapView(this, getMapApiKey());
            mapView.setEnabled(true);
            mapView.setClickable(true);
            mapView.setBuiltInZoomControls(true);
            mapController = mapView.getController();
            mapController.setZoom(19);
            mapOverlays = mapView.getOverlays();

            drawableGPS = getResources().getDrawable(R.drawable.ic_location_gps);
            drawableWifi = getResources().getDrawable(R.drawable.ic_location_wifi);

            LinearLayout linearLayoutMap = (LinearLayout)findViewById(R.id.linearLayoutMap);
            linearLayoutMap.addView(mapView);
        } //MapViewの準備

        { //マーケットでの評価のお願い。
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
            if(   "init".equals(pref.getString("rateIraisuruNitiji", "init"))==true   ){ //今回が初期起動。
                storeRateIraisuruNitiji(10);
            } //今回が初期起動。
            else{ //初期起動ではない。
                long milliseconds;
                try {
                    milliseconds = Long.valueOf(pref.getString("rateIraisuruNitiji", "init"));
                } catch (NumberFormatException e) {
                    milliseconds = 0;
                }

                Calendar nowDate = Calendar.getInstance();

                long sa_msec = nowDate.getTimeInMillis()-milliseconds;

                if(0 < sa_msec){
                    new AlertDialog.Builder(this)
                        .setTitle("Please rate and comment on Google Play. Please give your cooperation!")
                        .setPositiveButton("Yes Now",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        storeRateIraisuruNitiji(100);

                                        Uri uri = Uri.parse("market://details?id=info.nakajimadevnakajima.GpsWifiPosDifference");
                                        Intent i = new Intent(Intent.ACTION_VIEW,uri);
                                        startActivity(i);
                                    }
                                })
                        .setNeutralButton("Later",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        storeRateIraisuruNitiji(1);
                                    }
                                })
                        .setNegativeButton("No Never",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        storeRateIraisuruNitiji(30);
                                    }
                                })
                        .show();
                }
            } //初期起動ではない。
        } //マーケットでの評価のお願い。
    }


    //次に評価依頼をする日時を記録。
    void storeRateIraisuruNitiji(long dayAfter){
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor=pref.edit();

        Calendar nowDate = Calendar.getInstance();
        editor.putString("rateIraisuruNitiji", ""+(nowDate.getTimeInMillis()+dayAfter*24*60*60*1000L));

        editor.commit();
    }


    @Override
    protected void onPause() {
        // センサーリスナー登録解除
        locationGps.removeUpdates();
        locationWifi.removeUpdates();

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        locationGps.requestLocationUpdates();
        locationWifi.requestLocationUpdates();
    }

    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }


    //MapViewのため、パッケージ署名に対応するAPI Keyを返す
    private String getMapApiKey() {
        try {
            PackageManager pm = getPackageManager();
            PackageInfo pi = pm.getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);

            for (Signature sig : pi.signatures) {
                String sigStr;

                try {
                    MessageDigest md = MessageDigest.getInstance("MD5");
                    md.update(sig.toByteArray());
                    sigStr = hex(md.digest());
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                    return null;
                }

                // デバッグ用署名(debug.keystore)に対応するAPI Key
                if (sigStr.equals("23:17:1F:3B:BC:91:AA:9F:6B:F6:A9:1E:E7:0D:51:1C")) {
                    return "0vGGYQx8N8V6i29AKMOgR9uo1IhbdMo_cfWFJOw";
                }
                // リリース用署名に対応するAPI Key
                else if (sigStr.equals("F3:33:CA:32:07:75:A1:29:20:38:86:A3:98:54:2E:D9")) {
                    return "0vGGYQx8N8V4kg7DfGazqTqrIbp3aijZXaYvHDw";
                } else {
                    return null;
                }
            }
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

    // バイト列の16進文字列化
    private static String hex(byte[] bin) {
        StringBuilder sb = new StringBuilder(bin.length * 3 - 1);
        for (int i = 0; i < bin.length; i++) {
            sb.append(String.format("%02X", bin[i]));
            if (i + 1 < bin.length)
                sb.append(':');
        }
        return sb.toString();
    }

}
