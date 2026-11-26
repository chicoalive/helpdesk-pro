package com.helpdeskpro.backend.services;

import com.helpdeskpro.backend.exceptions.BusinessRuleException;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

/** Bounded PNG/JPEG validation using JDK decoders; nothing is written to the filesystem. */
public final class AttachmentImages {
    public static final int MAX_BYTES=2*1024*1024;
    private static final long MAX_PIXELS=16_777_216;
    private static final int MAX_SIDE=8192;
    private static final Semaphore DECODERS=new Semaphore(2);
    private AttachmentImages() {}
    public record Validated(byte[] bytes,String contentType,String name) {}
    private static BusinessRuleException invalid(){return new BusinessRuleException("Imagem inválida, incompleta ou incompatível. Envie um arquivo PNG ou JPEG válido.");}
    public static Validated upload(MultipartFile file) {
        if(file==null||file.isEmpty())throw new BusinessRuleException("O arquivo de imagem não pode estar vazio.");
        if(file.getSize()>MAX_BYTES)throw new BusinessRuleException("A imagem deve ter no máximo 2 MB.");
        try(var stream=file.getInputStream()){
            byte[] bytes=stream.readNBytes(MAX_BYTES+1);
            if(bytes.length>MAX_BYTES)throw new BusinessRuleException("A imagem deve ter no máximo 2 MB.");
            String type=validate(bytes),declared=file.getContentType();
            if(declared==null||!type.equalsIgnoreCase(declared.trim()))throw invalid();
            String name=file.getOriginalFilename();if(name==null||name.isBlank())name="anexo"+(type.equals("image/png")?".png":".jpg");
            if(name.length()>255||name.chars().anyMatch(c->c<32||c==127)||name.contains("/")||name.contains("\\")||name.contains(":"))throw new BusinessRuleException("Nome de arquivo inválido.");
            String lower=name.toLowerCase(Locale.ROOT);
            if(type.equals("image/png")?!lower.endsWith(".png"):!(lower.endsWith(".jpg")||lower.endsWith(".jpeg")))throw invalid();
            return new Validated(bytes,type,name);
        }catch(IOException e){throw new BusinessRuleException("Não foi possível processar o arquivo enviado.");}
    }
    public static String validate(byte[] bytes) {
        if(bytes==null||bytes.length==0||bytes.length>MAX_BYTES)throw invalid();
        String format;
        if(bytes.length>=8&&Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10})){png(bytes);format="png";}
        else if(bytes.length>=4&&(bytes[0]&255)==255&&(bytes[1]&255)==216){jpeg(bytes);format="jpeg";}
        else throw invalid();
        if(!DECODERS.tryAcquire())throw new BusinessRuleException("Processamento de imagem ocupado. Tente novamente em instantes.");
        ImageReader reader=null;java.awt.image.BufferedImage decoded=null;
        try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReadersByFormatName(format);if(!readers.hasNext())throw invalid();reader=readers.next();reader.setInput(input,true,true);
            AtomicBoolean warning=new AtomicBoolean();reader.addIIOReadWarningListener((source,message)->warning.set(true));
            dimensions(reader.getWidth(0),reader.getHeight(0));decoded=reader.read(0);
            if(decoded==null||warning.get())throw invalid();dimensions(decoded.getWidth(),decoded.getHeight());
            return format.equals("png")?"image/png":"image/jpeg";
        }catch(IOException|RuntimeException e){if(e instanceof BusinessRuleException business)throw business;throw invalid();}
        finally{if(decoded!=null)decoded.flush();if(reader!=null)reader.dispose();DECODERS.release();}
    }
    private static void dimensions(int width,int height){if(width<1||height<1||width>MAX_SIDE||height>MAX_SIDE||(long)width*height>MAX_PIXELS)throw new BusinessRuleException("A imagem excede o limite seguro de dimensões (8192 por lado e 16 megapixels).");}
    private static int integer(byte[] b,int p){return ByteBuffer.wrap(b,p,4).order(ByteOrder.BIG_ENDIAN).getInt();}
    private static void png(byte[] b){int p=8;boolean header=false,data=false,endedData=false;
        while(p<b.length){if(b.length-p<12)throw invalid();long length=Integer.toUnsignedLong(integer(b,p));if(length>b.length-p-12)throw invalid();int n=(int)length;String type=new String(b,p+4,4,java.nio.charset.StandardCharsets.US_ASCII);
            if(!type.matches("[A-Za-z]{4}") || !Character.isUpperCase(type.charAt(2)) || Set.of("acTL","fcTL","fdAT").contains(type))throw invalid();CRC32 crc=new CRC32();crc.update(b,p+4,n+4);if(crc.getValue()!=Integer.toUnsignedLong(integer(b,p+8+n)))throw invalid();
            if(!header){if(!type.equals("IHDR")||n!=13)throw invalid();dimensions(integer(b,p+8),integer(b,p+12));header=true;}
            else if(type.equals("IHDR"))throw invalid();
            if(type.equals("IDAT")){if(endedData)throw invalid();data=true;}else if(data)endedData=true;
            if(type.equals("IEND")){if(n!=0||!data||p+12!=b.length)throw invalid();return;}
            if(Character.isUpperCase(type.charAt(0))&&!Set.of("IHDR","PLTE","IDAT","IEND").contains(type))throw invalid();
            p+=12+n;
        }throw invalid();
    }
    private static void jpeg(byte[] b){int p=2;boolean scan=false,frame=false,sawScan=false;
        while(p<b.length){int marker;
            if(scan){while(p<b.length&&(b[p]&255)!=255)p++;if(p>=b.length)throw invalid();}
            if((b[p++]&255)!=255)throw invalid();while(p<b.length&&(b[p]&255)==255)p++;if(p>=b.length)throw invalid();marker=b[p++]&255;
            if(scan&&(marker==0||(marker>=208&&marker<=215)))continue;
            scan=false;
            if(marker==217){if(!frame||!sawScan||p!=b.length)throw invalid();return;}
            if(marker==216||marker==0||marker>=208&&marker<=215)throw invalid();if(marker==1)continue;
            if(p+2>b.length)throw invalid();int n=((b[p]&255)<<8)|(b[p+1]&255);if(n<2||n>b.length-p)throw invalid();
            if(Set.of(192,193,194,195,197,198,199,201,202,203,205,206,207).contains(marker)){if(n<8)throw invalid();dimensions(((b[p+5]&255)<<8)|(b[p+6]&255),((b[p+3]&255)<<8)|(b[p+4]&255));frame=true;}
            p+=n;if(marker==218){if(!frame)throw invalid();scan=true;sawScan=true;}
        }throw invalid();
    }
}