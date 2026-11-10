import javax.imageio.ImageIO; import java.io.File; import java.util.*;
public class Chat { public static void main(String[] a) throws Exception { var i=ImageIO.read(new File(a[0])); List<int[]> l=new ArrayList<>();
 for(int y=384;y<399;y++)for(int x=6;x<90;x++){int p=i.getRGB(x,y);l.add(new int[]{p>>16&255,p>>8&255,p&255});}
 // glyph = most saturated (max-min channel) 8%
 l.sort(Comparator.comparingInt((int[] c)->Math.max(c[0],Math.max(c[1],c[2]))-Math.min(c[0],Math.min(c[1],c[2]))).reversed()); int n=l.size()/12; long r=0,g=0,b=0; for(int k=0;k<n;k++){r+=l.get(k)[0];g+=l.get(k)[1];b+=l.get(k)[2];}
 System.out.println(new File(a[0]).getName()+" chat glyph (most saturated 8%) = ("+r/n+","+g/n+","+b/n+")");}}
