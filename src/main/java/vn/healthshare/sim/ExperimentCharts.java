package vn.healthshare.sim;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/** Three separate measured curves with the axes used in Figure 3; no unimplemented baseline. */
public final class ExperimentCharts {
    private ExperimentCharts(){}
    public static void writePng(List<Benchmark.Result> rows,Path path)throws IOException{
        BufferedImage image=new BufferedImage(1800,620,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();draw(g,1800,620,rows);g.dispose();ImageIO.write(image,"png",path.toFile());
    }
    public static void draw(Graphics2D g,int width,int height,List<Benchmark.Result> rows){
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);g.fillRect(0,0,width,height);g.setFont(new Font("Segoe UI",Font.PLAIN,12));
        if(rows.isEmpty()){g.setColor(new Color(94,112,124));g.drawString("Chạy benchmark để xem ba biểu đồ thực nghiệm JPBC.",25,55);return;}
        String[] names={"(a) Encrypt ABE","(b) KeyGen CA + AA","(c) Decrypt ABE"};
        int panel=width/3;
        for(int mode=0;mode<3;mode++){
            int left=mode*panel+60,right=(mode+1)*panel-20,top=58,bottom=height-68;
            g.setColor(new Color(24,47,65));g.setFont(new Font("Segoe UI",Font.BOLD,13));g.drawString(names[mode]+" (s)",left,20);g.setFont(new Font("Segoe UI",Font.PLAIN,11));
            double max=0;for(var r:rows)max=Math.max(max,value(r,mode));max=Math.max(.001,max*1.15);
            for(int tick=0;tick<=4;tick++){
                int y=bottom-(bottom-top)*tick/4;g.setColor(new Color(226,233,237));g.drawLine(left,y,right,y);g.setColor(Color.GRAY);g.drawString(String.format(java.util.Locale.ROOT,"%.2f",max*tick/4),left-49,y+4);
            }
            g.setColor(new Color(45,86,190));g.setStroke(new BasicStroke(2));int lastX=0,lastY=0;
            for(int i=0;i<rows.size();i++){
                var r=rows.get(i);int x=left+(right-left)*i/Math.max(1,rows.size()-1),y=bottom-(int)((bottom-top)*value(r,mode)/max);
                if(i>0)g.drawLine(lastX,lastY,x,y);g.fillRect(x-3,y-3,6,6);lastX=x;lastY=y;
                String lower=""+(mode==1?r.satisfyingAttributes():r.attributes());g.drawString(lower,x-g.getFontMetrics().stringWidth(lower)/2,bottom+17);
                if(mode>0){String upper=""+(mode==1?r.attributes():r.satisfyingAttributes());g.drawString(upper,x-g.getFontMetrics().stringWidth(upper)/2,top-10);}
            }
            g.setColor(new Color(75,90,105));String xLabel=mode==1?"Thuộc tính trong tập thỏa":"Thuộc tính trong chính sách";
            g.drawString(xLabel,left+(right-left-g.getFontMetrics().stringWidth(xLabel))/2,bottom+37);
            if(mode>0){String upper=mode==1?"Thuộc tính trong chính sách":"Thuộc tính trong tập thỏa";g.drawString(upper,left+(right-left-g.getFontMetrics().stringWidth(upper))/2,top-27);}
        }
        g.setColor(new Color(75,90,105));g.drawString("SP-MAACS · JPBC Type A1 · 3 × "+rows.getFirst().primeBits()+" bit · "+rows.getFirst().runs()+" lượt/điểm · không có số liệu đối chứng [26]",25,height-10);
    }
    private static double value(Benchmark.Result r,int mode){return(mode==0?r.encryptionMs():mode==1?r.keygenMs():r.decryptionMs())/1000.0;}
}
