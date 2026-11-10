import javax.imageio.ImageIO; import java.io.File; import java.util.*;
public class Glyph { public static void main(String[] a) throws Exception { var i=ImageIO.read(new File(a[0])); int x0=Integer.parseInt(a[1]),y0=Integer.parseInt(a[2]),x1=Integer.parseInt(a[3]),y1=Integer.parseInt(a[4]);
 List<int[]> l=new ArrayList<>(); for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){int p=i.getRGB(x,y);l.add(new int[]{p>>16&255,p>>8&255,p&255});}
 l.sort(Comparator.comparingInt(c->c[0]+c[1]+c[2])); int n=Math.max(1,l.size()/20); long r=0,g=0,b=0; for(int k=0;k<n;k++){r+=l.get(k)[0];g+=l.get(k)[1];b+=l.get(k)[2];}
 long R=0,G=0,B=0; int m=l.size()/2; for(int k=m;k<l.size();k++){R+=l.get(k)[0];G+=l.get(k)[1];B+=l.get(k)[2];}
 System.out.printf("%s darkest5%% (glyph)=(%d,%d,%d) upper-half mean (plate/bg)=(%d,%d,%d)%n",new File(a[0]).getName(),r/n,g/n,b/n,R/(l.size()-m),G/(l.size()-m),B/(l.size()-m));}}
