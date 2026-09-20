package com.local.focusfence.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.*;
import android.widget.*;
import com.local.focusfence.R;

/** Small native design system. Illustrations contain no UI text; everything else is accessible text. */
public final class Ui {
    public static final int PAPER=0xFFF5F0E5, SURFACE=0xFFFFFCF7, INK=0xFF302921,
        MUTED=0xFF756E60, FOREST=0xFF365A43, SAGE=0xFFE4EAD9, LINE=0xFFE5DFD1,
        DARK=0xFF17291F, CREAM=0xFFF9F1DC, RUST=0xFF9D5145, PALE_RED=0xFFF3E5DF;
    private Ui(){}
    public static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    public static LinearLayout col(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    public static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    public static LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w,h);}
    public static LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1f);}
    public static LinearLayout.LayoutParams top(Context c,int margin){LinearLayout.LayoutParams l=lp(-1,-2);l.topMargin=dp(c,margin);return l;}
    public static void pad(View v,int l,int t,int r,int b){v.setPadding(dp(v.getContext(),l),dp(v.getContext(),t),dp(v.getContext(),r),dp(v.getContext(),b));}
    public static GradientDrawable round(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    public static TextView text(Context c,String text,int size,int color,boolean bold){
        TextView t=new TextView(c);t.setText(text);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        t.setIncludeFontPadding(false);t.setLineSpacing(dp(c,2),1f);return t;
    }
    public static TextView title(Context c,String s,int size){return text(c,s,size,INK,true);}
    public static TextView muted(Context c,String s,int size){return text(c,s,size,MUTED,false);}
    public static void space(LinearLayout p,int height){p.addView(new View(p.getContext()),lp(1,dp(p.getContext(),height)));}
    public static void line(LinearLayout p){View l=new View(p.getContext());l.setBackgroundColor(LINE);p.addView(l,top(p.getContext(),10));l.getLayoutParams().height=dp(p.getContext(),1);}
    public static LinearLayout card(Context c){LinearLayout l=col(c);GradientDrawable bg=round(c,SURFACE,22);bg.setStroke(dp(c,1),LINE);l.setBackground(bg);pad(l,16,16,16,16);return l;}
    public static TextView pill(Context c,String s,boolean good){TextView t=text(c,s,11,good?FOREST:RUST,true);pad(t,10,6,10,6);t.setBackground(round(c,good?SAGE:PALE_RED,18));return t;}
    public static TextView button(Context c,String s,boolean primary,Runnable click){
        TextView t=text(c,s,15,primary?SURFACE:FOREST,true);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(c,50));pad(t,16,13,16,13);
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x28365A43),round(c,primary?FOREST:SAGE,17),null));
        t.setFocusable(true);t.setClickable(true);t.setOnClickListener(v->click.run());t.setContentDescription(s);
        return t;
    }
    public static ImageView image(Context c,int res,int height,int radius){
        ImageView im=new ImageView(c);im.setImageResource(res);im.setScaleType(ImageView.ScaleType.CENTER_CROP);
        im.setBackground(round(c,SAGE,radius));im.setClipToOutline(true);im.setLayoutParams(lp(-1,dp(c,height)));
        im.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return im;
    }
    public static ImageView avatar(Context c,int size){ImageView a=image(c,R.drawable.bernard_avatar,size,999);a.setLayoutParams(lp(dp(c,size),dp(c,size)));return a;}
    public static void insets(Activity a,View root,boolean dark){
        if(Build.VERSION.SDK_INT>=30){a.getWindow().setDecorFitsSystemWindows(false);}
        else{a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);}
        a.getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        a.getWindow().setNavigationBarColor(dark?DARK:PAPER);
        if(Build.VERSION.SDK_INT>=30){WindowInsetsController controller=a.getWindow().getInsetsController();if(controller!=null)controller.setSystemBarsAppearance(dark?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);}
        else if(!dark){a.getWindow().getDecorView().setSystemUiVisibility(a.getWindow().getDecorView().getSystemUiVisibility()|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|(Build.VERSION.SDK_INT>=26?View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0));}
        root.setOnApplyWindowInsetsListener((v,w)->{
            if(Build.VERSION.SDK_INT>=30){Insets i=w.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(i.left,i.top,i.right,i.bottom);}
            else v.setPadding(w.getSystemWindowInsetLeft(),w.getSystemWindowInsetTop(),w.getSystemWindowInsetRight(),w.getSystemWindowInsetBottom());
            return w;
        });root.requestApplyInsets();
    }
    public static Icon icon(Context c,String name,int color,int size){Icon i=new Icon(c,name,color);i.setLayoutParams(lp(dp(c,size),dp(c,size)));return i;}
    public static View iconButton(Context c,String name,String description,Runnable click){FrameLayout f=new FrameLayout(c);Icon i=icon(c,name,INK,23);FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(c,23),dp(c,23),Gravity.CENTER);f.addView(i,ip);f.setLayoutParams(lp(dp(c,46),dp(c,46)));f.setBackground(new RippleDrawable(ColorStateList.valueOf(SAGE),round(c,Color.TRANSPARENT,24),null));f.setContentDescription(description);f.setFocusable(true);f.setOnClickListener(v->click.run());return f;}
    public static View progress(Context c,long used,long cap){return new Bar(c,cap<=0?0:Math.min(1f,used/(float)cap),cap>0&&used>=cap?RUST:FOREST);}
    public static final class Bar extends View{
        private final float fraction;private final int color;private final Paint p=new Paint(3);
        public Bar(Context c,float f,int color){super(c);fraction=f;this.color=color;setLayoutParams(lp(-1,dp(c,7)));setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        protected void onDraw(Canvas c){p.setColor(LINE);c.drawRoundRect(0,0,getWidth(),getHeight(),20,20,p);p.setColor(color);if(fraction>0)c.drawRoundRect(0,0,getWidth()*fraction,getHeight(),20,20,p);}
    }
    public static final class Icon extends View {
        private final String name;private final int color;private final Paint p=new Paint(3);
        Icon(Context c,String name,int color){super(c);this.name=name;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        private void poly(Canvas c,float...xy){Path x=new Path();x.moveTo(xy[0],xy[1]);for(int j=2;j<xy.length;j+=2)x.lineTo(xy[j],xy[j+1]);c.drawPath(x,p);}
        protected void onDraw(Canvas canvas){super.onDraw(canvas);Canvas c=canvas;int save=c.save();c.scale(getWidth()/24f,getHeight()/24f);p.setColor(color);p.setStrokeWidth(1.75f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);
            switch(name){
                case "home":poly(c,3,11,12,3,21,11);poly(c,5,10,5,21,10,21,10,14,14,14,14,21,19,21,19,10);break;
                case "shield":{Path q=new Path();q.moveTo(12,2);q.lineTo(21,6);q.lineTo(20,13);q.quadTo(18,19,12,22);q.quadTo(6,19,4,13);q.lineTo(3,6);q.close();c.drawPath(q,p);break;}
                case "gift":c.drawRect(4,10,20,21,p);c.drawRect(2,7,22,11,p);c.drawLine(12,7,12,21,p);c.drawOval(5,2,12,7,p);c.drawOval(12,2,19,7,p);break;
                case "history":c.drawLine(5,20,5,13,p);c.drawLine(12,20,12,4,p);c.drawLine(19,20,19,9,p);break;
                case "clock":c.drawCircle(12,12,9,p);poly(c,12,6,12,12,16,14);break;
                case "back":poly(c,14,5,7,12,14,19);c.drawLine(7,12,22,12,p);break;
                case "chevron":poly(c,9,5,16,12,9,19);break;
                case "check":poly(c,4,12,9,17,20,6);break;
                case "plus":c.drawLine(12,4,12,20,p);c.drawLine(4,12,20,12,p);break;
                case "play":{Path q=new Path();q.moveTo(8,5);q.lineTo(19,12);q.lineTo(8,19);q.close();c.drawPath(q,p);break;}
                case "game":c.drawRoundRect(2,5,22,20,5,5,p);c.drawLine(8,9,8,15,p);c.drawLine(5,12,11,12,p);p.setStyle(Paint.Style.FILL);c.drawCircle(17,10,1.2f,p);c.drawCircle(19,14,1.2f,p);break;
                case "lock":c.drawRoundRect(5,10,19,21,2,2,p);c.drawArc(7,2,17,15,180,180,false,p);c.drawLine(12,14,12,17,p);break;
                case "leaf":{Path q=new Path();q.moveTo(4,19);q.quadTo(2,4,21,3);q.quadTo(20,22,4,19);c.drawPath(q,p);c.drawLine(3,21,17,7,p);break;}
                case "settings":c.drawCircle(12,12,6.5f,p);c.drawCircle(12,12,2.4f,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine(12+(float)Math.cos(a)*7,12+(float)Math.sin(a)*7,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10,p);}break;
                case "download":poly(c,12,3,12,15);poly(c,7,10,12,15,17,10);poly(c,4,17,4,21,20,21,20,17);break;
                default:c.drawCircle(12,12,8,p);c.drawLine(12,7,12,13,p);c.drawPoint(12,17,p);
            }c.restoreToCount(save);
        }
    }
    public static View blockScreen(Context c,String person,String reason,String resume,boolean shortContent,boolean schedule,Runnable exit,Runnable limits){
        LinearLayout root=col(c);root.setBackgroundColor(DARK);ScrollView scroll=new ScrollView(c);scroll.setFillViewport(true);LinearLayout body=col(c);pad(body,22,16,22,26);scroll.addView(body);
        LinearLayout brand=row(c);brand.addView(avatar(c,34));TextView b=text(c,"BERNARD BLOQUEUR",13,CREAM,true);pad(b,10,0,0,0);brand.addView(b);body.addView(brand);space(body,18);
        TextView title=text(c,schedule?"Pas encore…":"La barrière est fermée.",30,CREAM,true);body.addView(title);space(body,8);body.addView(text(c,"🐗 Bernard garde le cap avec toi.",14,0xFFCAD5C5,false));space(body,20);
        ImageView scene=image(c,schedule?R.drawable.scene_home:R.drawable.scene_block,220,24);body.addView(scene);space(body,22);
        TextView r=text(c,reason,20,CREAM,true);body.addView(r);space(body,12);body.addView(text(c,resume,15,0xFFCED7CA,false));space(body,12);
        body.addView(text(c,"Tu as choisi cette limite. Bernard s'en occupe.",14,0xFFCED7CA,false));space(body,26);
        TextView primary=button(c,shortContent?"Revenir à l’application":"Retour à l’accueil",false,exit);primary.setBackground(round(c,CREAM,18));primary.setTextColor(DARK);body.addView(primary,lp(-1,-2));space(body,10);
        TextView secondary=button(c,"Voir mes limites",true,limits);secondary.setBackground(round(c,0xFF294335,18));body.addView(secondary,lp(-1,-2));space(body,16);
        if(shortContent)body.addView(text(c,"Les messages restent accessibles. Seule la zone limitée est fermée.",12,0xFFCED7CA,false));
        root.addView(scroll,lp(-1,-1));return root;
    }
}
