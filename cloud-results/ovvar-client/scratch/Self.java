import javax.imageio.ImageIO; import java.io.File;
public class Self { public static void main(String[] a) throws Exception { var A=ImageIO.read(new File(a[0])); var B=ImageIO.read(new File(a[1])); int big=0;
 for(int y=560;y<800;y++)for(int x=780;x<1140;x++){int p=A.getRGB(x,y),q=B.getRGB(x,y); if(Math.max(Math.abs((p>>16&255)-(q>>16&255)),Math.max(Math.abs((p>>8&255)-(q>>8&255)),Math.abs((p&255)-(q&255))))>24)big++;}
 System.out.println(a[0]+" vs "+a[1]+": "+big+" px >24 in subject box");}}
