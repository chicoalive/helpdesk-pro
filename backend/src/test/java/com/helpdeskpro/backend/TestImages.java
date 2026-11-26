package com.helpdeskpro.backend;
public final class TestImages {
 private TestImages() {}
 public static byte[] png(){return image("png");} public static byte[] jpeg(){return image("jpeg");}
 private static byte[] image(String format){try{var image=new java.awt.image.BufferedImage(3,2,java.awt.image.BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,0x1683D8);var output=new java.io.ByteArrayOutputStream();if(!javax.imageio.ImageIO.write(image,format,output))throw new IllegalStateException("Missing JDK codec");image.flush();return output.toByteArray();}catch(java.io.IOException e){throw new IllegalStateException(e);}}
}
