package ru.chekskan;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import com.googlecode.tesseract.android.TessBaseAPI;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
 static final int CAMERA=10,GALLERY=11; TextView status,result,summary; Uri photo;
 ArrayList<Item> items=new ArrayList<>(); String date="";
 @Override public void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_main);
  status=findViewById(R.id.status);result=findViewById(R.id.result);summary=findViewById(R.id.summary);
  findViewById(R.id.scanButton).setOnClickListener(v->camera());
  findViewById(R.id.galleryButton).setOnClickListener(v->gallery());
  findViewById(R.id.saveButton).setOnClickListener(v->{getPreferences(0).edit().putString("last",result.getText().toString()).apply();Toast.makeText(this,"Чек сохранён",Toast.LENGTH_SHORT).show();});
  findViewById(R.id.exportButton).setOnClickListener(v->exportCsvXlsx());
  findViewById(R.id.historyButton).setOnClickListener(v->Toast.makeText(this,"История доступна после сохранения",Toast.LENGTH_SHORT).show());
 }
 void camera(){if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA);return;}
  try{File d=new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),"receipts");d.mkdirs();File f=new File(d,"r_"+System.currentTimeMillis()+".jpg");photo=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);i.putExtra(MediaStore.EXTRA_OUTPUT,photo);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);startActivityForResult(i,CAMERA);}catch(Exception e){Toast.makeText(this,e.toString(),Toast.LENGTH_LONG).show();}
 }
 void gallery(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,GALLERY);}
 @Override protected void onActivityResult(int r,int c,@Nullable Intent d){super.onActivityResult(r,c,d);if(c!=RESULT_OK)return;try{Uri u=r==CAMERA?photo:d.getData();Bitmap b=MediaStore.Images.Media.getBitmap(getContentResolver(),u);ocr(b);}catch(Exception e){status.setText("Не удалось открыть чек: "+e.getMessage());}}
 void ocr(Bitmap src){status.setText("Распознаю русский текст…");Bitmap b=prep(src);new Thread(()->{String text="";try{File base=new File(getFilesDir(),"tesseract");File td=new File(base,"tessdata");td.mkdirs();copy("tessdata/rus.traineddata",new File(td,"rus.traineddata"));copy("tessdata/eng.traineddata",new File(td,"eng.traineddata"));TessBaseAPI t=new TessBaseAPI();if(!t.init(base.getAbsolutePath(),"rus+eng"))throw new Exception("русский OCR не загрузился");
   String[] ps={"4","6","11"};String best="";for(String p:ps){t.setPageSegMode(Integer.parseInt(p));t.setImage(b);String x=t.getUTF8Text();if(score(x)>score(best))best=x;}t.release();text=best;}catch(Exception e){final String m=e.getMessage();runOnUiThread(()->status.setText("Ошибка OCR: "+m));return;}final String x=text;runOnUiThread(()->parse(x));}).start();}
 int score(String s){if(s==null)return 0;int n=s.length();String q=s.toLowerCase(Locale.ROOT);for(String k:new String[]{"итого","цена","кол-во","пиво","молоко","товар","шт","руб","₽"})if(q.contains(k))n+=500;return n;}
 void copy(String a,File f)throws IOException{if(f.exists()&&f.length()>100000)return;try(InputStream i=getAssets().open(a);OutputStream o=new FileOutputStream(f)){byte[] z=new byte[8192];int n;while((n=i.read(z))>0)o.write(z,0,n);}}
 Bitmap prep(Bitmap s){int w=Math.max(1800,s.getWidth()),h=(int)(s.getHeight()*w/(double)s.getWidth());Bitmap b=Bitmap.createScaledBitmap(s,w,h,true);Bitmap g=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(g);Paint p=new Paint();ColorMatrix m=new ColorMatrix();m.setSaturation(0);p.setColorFilter(new android.graphics.ColorMatrixColorFilter(m));c.drawBitmap(b,0,0,p);return g;}
 void parse(String text){items.clear();date=find(text,"(?m)(\\d{2}\\.\\d{2}\\.(?:\\d{2}|\\d{4}))");if(date==null)date=new SimpleDateFormat("dd.MM.yyyy",Locale.US).format(new Date());
   String[] ls=text.split("\\r?\\n");for(int i=0;i<ls.length;i++){String line=ls[i].trim();if(line.length()<3)continue;if(isSkip(line))continue;
    Matcher same=Pattern.compile("(.+?)\\s+(\\d+[,.]\\d+)\\s*[xх*]\\s*(\\d+[,.]\\d+)\\s*(?:=\\s*)?(\\d+[,.]\\d+)").matcher(line);
    if(same.find()){add(clean(same.group(1)),num(same.group(3)),num(same.group(2)),num(same.group(4)));continue;}
    if(i+1<ls.length){Matcher nxt=Pattern.compile("(?i).*?(\\d+(?:[,.]\\d+)?)\\s*(?:шт|кг|г|л|мл)?\\s*[xх*]\\s*(\\d+[,.]\\d+).*").matcher(ls[i+1].trim());if(nxt.matches()&&hasLetters(line)){String next=ls[i+1].trim();Matcher q=Pattern.compile("(\\d+(?:[,.]\\d+)?)\\s*(?:шт|кг|г|л|мл)?\\s*[xх*]\\s*(\\d+[,.]\\d+)").matcher(next);if(q.find()){double qty=num(q.group(1)),unit=num(q.group(2));double sum=unit*qty;if(i+2<ls.length){Matcher sm=Pattern.compile("=\\s*(\\d+[,.]\\d+)").matcher(ls[i+2]);if(sm.find())sum=num(sm.group(1));}add(clean(line),unit,qty,sum);i++;}}}
   }render();}
 boolean hasLetters(String s){return s.matches(".*[А-Яа-яA-Za-zЁё].*");}
 boolean isSkip(String s){String q=s.toLowerCase(Locale.ROOT);return q.matches(".*(итого|к оплате|безналич|кассир|касса|адрес|место расчетов|фн |фд |фп |рн ккт|зн ккт|чек \\d|налог|сумма ндс|приглашаем|сайт фнс|покупатель).*")||s.matches(".*\\b(19|20)\\d{2}\\b.*");}
 void add(String n,double unit,double qty,double sum){if(n.length()<2)return;boolean alc=isAlcohol(n);items.add(new Item(n,unit,qty,sum,alc));}
 boolean isAlcohol(String s){String q=s.toLowerCase(Locale.ROOT).replace('ё','е');for(String k:new String[]{"пиво","пив","лагер","эль","портер","стаут","сидр","вино","водка","коньяк","виски","бренди","шампан","игрист","ликер","ром","джин","текил","вермут"})if(q.contains(k))return true;return false;}
 String clean(String s){return s.replaceAll("\\s{2,}"," ").replaceAll("\\b(22|10)\\s*%\\b","").trim();}
 double num(String s){return Double.parseDouble(s.replace(',','.'));}
 String find(String s,String r){Matcher m=Pattern.compile(r).matcher(s);return m.find()?m.group(1):null;}
 void render(){StringBuilder x=new StringBuilder();double total=0;for(Item a:items){x.append(a.alc?"🔴 ":"🔵 ").append(a.name).append(" — ").append(fmt(a.sum)).append(" ₽ × ").append(fmtq(a.qty)).append("\\n");total+=a.sum;}result.setText(x.length()==0?"Позиции не найдены — попробуйте другое фото.":x.toString());summary.setText("ВСЕГО: "+fmt(total)+" ₽");status.setText("Распознано позиций: "+items.size());}
 String fmt(double x){return String.format(Locale.US,"%.2f",x).replace('.',',');}String fmtq(double x){return String.format(Locale.US,"%.3f",x).replaceAll("0+$","").replaceAll("\\.$","").replace('.',',');}
 void exportCsvXlsx(){if(items.isEmpty()){Toast.makeText(this,"Сначала распознайте чек",Toast.LENGTH_SHORT).show();return;}try{File f=new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),"chekskan_"+System.currentTimeMillis()+".csv");try(PrintWriter p=new PrintWriter(new OutputStreamWriter(new FileOutputStream(f),"UTF-8"))){p.println("Дата;Продукты;Цена;Количество;Спиртное;Цена;Количество;Всего трат");double pt=0,at=0;for(Item a:items){if(a.alc){p.println(date+";;; ;"+a.name+";"+fmt(a.unit)+";"+fmtq(a.qty)+";"+fmt(a.sum));at+=a.sum;}else{p.println(date+";"+a.name+";"+fmt(a.unit)+";"+fmtq(a.qty)+";;;;"+fmt(a.sum));pt+=a.sum;}}p.println("ИТОГО;"+fmt(pt)+";;; ;"+fmt(at)+";;"+fmt(pt+at));}Toast.makeText(this,"Файл Excel-совместимый сохранён в Documents",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,e.toString(),Toast.LENGTH_LONG).show();}}
 static class Item{String name;double unit,qty,sum;boolean alc;Item(String n,double u,double q,double s,boolean a){name=n;unit=u;qty=q;sum=s;alc=a;}}
}
