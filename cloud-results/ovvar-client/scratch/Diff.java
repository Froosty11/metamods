import javax.imageio.ImageIO; import java.awt.image.BufferedImage; import java.io.File;
public class Diff { public static void main(String[] a) throws Exception {
 for (int i=2;i<a.length;i++){ BufferedImage A=ImageIO.read(new File(a[0]+"/"+a[i])),B=ImageIO.read(new File(a[1]+"/"+a[i]));
  if(A.getWidth()!=B.getWidth()||A.getHeight()!=B.getHeight()){System.out.println(a[i]+" size differs");continue;}
  long nz=0,big=0,sum=0; int minx=1<<30,miny=1<<30,maxx=-1,maxy=-1; int n=A.getWidth()*A.getHeight();
  for(int y=0;y<A.getHeight();y++)for(int x=0;x<A.getWidth();x++){int p=A.getRGB(x,y),q=B.getRGB(x,y);
   int d=Math.max(Math.abs((p>>16&255)-(q>>16&255)),Math.max(Math.abs((p>>8&255)-(q>>8&255)),Math.abs((p&255)-(q&255))));
   if(d>0){nz++;sum+=d;} if(d>24){big++;minx=Math.min(minx,x);miny=Math.min(miny,y);maxx=Math.max(maxx,x);maxy=Math.max(maxy,y);}}
  System.out.printf("%s px=%d nonzero=%d >24=%d (%.3f%%) bbox>24=[%d,%d..%d,%d]%n",a[i],n,nz,big,100.0*big/n,minx,miny,maxx,maxy);}}}
