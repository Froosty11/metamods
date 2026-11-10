import javax.imageio.ImageIO; import java.io.File; import java.util.*;
public class Sat { public static void main(String[] a) throws Exception { for(String f:a){var i=ImageIO.read(new File(f)); List<int[]> l=new ArrayList<>();
 for(int y=320;y<372;y++)for(int x=830;x<1085;x++){int p=i.getRGB(x,y);l.add(new int[]{p>>16&255,p>>8&255,p&255});}
 l.sort(Comparator.comparingInt((int[] c)->Math.abs(c[0]-c[2])+Math.abs(c[0]-c[1])+Math.abs(c[1]-c[2])).reversed()); int n=l.size()/25; long r=0,g=0,b=0; for(int k=0;k<n;k++){r+=l.get(k)[0];g+=l.get(k)[1];b+=l.get(k)[2];}
 System.out.println(new File(f).getName()+" glyph (most saturated 4%) = ("+r/n+","+g/n+","+b/n+")");}}}
