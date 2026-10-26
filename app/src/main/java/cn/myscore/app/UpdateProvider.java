package cn.myscore.app;

import android.app.DownloadManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Grants the installer read access to exactly the verified pending APK. */
public final class UpdateProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private File file(Uri uri)throws FileNotFoundException {
        UpdateRepository repository=new UpdateRepository(getContext());UpdateRelease release=repository.pending();
        try {
            if(release==null||!uri.getAuthority().equals(getContext().getPackageName()+".updates")||!uri.getPathSegments().equals(java.util.List.of("apk",release.version))||!release.sha256.equals(repository.preferences.getString("verified",""))||repository.progress().status()!=DownloadManager.STATUS_SUCCESSFUL)throw new FileNotFoundException("更新文件尚未校验或地址无效");
            File file=repository.file(release);if(!file.isFile())throw new FileNotFoundException("更新文件已不存在");return file;
        } catch(java.io.IOException e){throw new FileNotFoundException(e.getMessage());}
    }
    @Override public String getType(Uri uri){return "application/vnd.android.package-archive";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {if(!"r".equals(mode))throw new FileNotFoundException("仅允许读取更新文件");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
        try {
            File file=file(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            MatrixCursor cursor=new MatrixCursor(columns);Object[] values=new Object[columns.length];
            for(int i=0;i<columns.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))values[i]=file.getName();else if(OpenableColumns.SIZE.equals(columns[i]))values[i]=file.length();}
            cursor.addRow(values);return cursor;
        }catch(FileNotFoundException e){return null;}
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
}
