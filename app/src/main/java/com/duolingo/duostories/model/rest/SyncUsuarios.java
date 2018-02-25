package com.duolingo.duostories.model.rest;

import android.graphics.Bitmap;
import android.view.View;

import com.android.volley.Request;
import com.duolingo.duostories.MyApplication;
import com.duolingo.duostories.model.entities.Curso;
import com.duolingo.duostories.model.entities.Usuario;
import com.duolingo.duostories.model.rest.listener.UsuarioRESTListener;
import com.duolingo.duostories.model.utils.ImageUtils;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import br.agr.terras.aurora.log.Logger;
import br.agr.terras.aurora.rest.EnvioJson;
import br.agr.terras.aurora.rest.EnvioListener;
import io.realm.RealmList;

/**
 * Created by leo on 23/02/18.
 */

public class SyncUsuarios {
    private static final String URL_INFO_USUARIO = "https://www.duolingo.com/2017-06-30/users?username=";

    public void baixarUsuario(final String username, final UsuarioRESTListener restListener){
        String url = URL_INFO_USUARIO + username.trim();
        EnvioJson envioJson = new EnvioJson(Request.Method.GET, url, null, null, new EnvioListener() {
            @Override
            public void onError(Exception e, int code) {
                restListener.erro();
            }

            @Override
            public void onSuccess(JSONObject result) {
                try {
                    Usuario usuario = parserJsonParaUsuario(result);
                    restListener.usuarioBaixado(usuario);
                    baixarFoto(usuario.getId(), usuario.getImagemUrl(), atualizarFotoBaixada(usuario, restListener, 1));
                } catch (IOException e) {
                    restListener.erro();
                } catch (JSONException e) {
                    restListener.erroUsuarioNaoExiste(username);
                }
            }

            @Override
            public void onSuccess(String result) {
                onError(new IOException(), 0);
            }
        });
        envioJson.sync(MyApplication.getActivity());
    }

    private Usuario parserJsonParaUsuario(JSONObject jsonObject) throws IOException, JSONException{
        JSONArray users = jsonObject.getJSONArray("users");
        if (users.length()==0)
            throw new IOException();
        JSONObject user = users.getJSONObject(0);
        Usuario usuario = new Usuario();
        usuario.setOfensiva(user.optInt("streak"));
        usuario.setImagemUrl("http:"+user.getString("picture")+"/xlarge");
        usuario.setNome(user.optString("name","Sem Nome"));
        usuario.setId(user.getString("username"));
        usuario.setPlus(user.optBoolean("hasPlus"));
        usuario.setXp(user.optInt("totalXp"));
        usuario.setBio(user.optString("bio"));
        usuario.setMetaDiaria(user.optInt("xpGoal"));
        usuario.setEmail(user.optString("email"));
        usuario.setLingots(user.optInt("lingots"));
        usuario.setLocal(user.optString("location"));
        RealmList<Curso> cursos = new RealmList<>();
        JSONArray courses = user.optJSONArray("courses");
        if (courses!=null && courses.length()>0)
            for (int i=0; i<courses.length(); i++){
                JSONObject course = courses.getJSONObject(i);
                Curso curso = new Curso();
                curso.setId(course.getString("id"));
                curso.setIdioma(course.getString("title"));
                curso.setLinguaNativa(course.getString("fromLanguage"));
                curso.setCodigo(course.getString("learningLanguage"));
                curso.setXp(course.getInt("xp"));
                cursos.add(curso);
            }
        usuario.setCursos(cursos);
        return usuario;
    }

    private void baixarFoto(String id, String url, SimpleImageLoadingListener loadingListener){
        String path = MyApplication.getActivity().getFilesDir().getPath()+ File.separator+"pictures"+File.separator;
        File child = new File(path, id+".jpg");
        if (!child.exists())
            ImageUtils.getImageLoader().loadImage(url, loadingListener);
    }

    private SimpleImageLoadingListener atualizarFotoBaixada(final Usuario usuario, final UsuarioRESTListener usuarioRESTListener, final int tentativa){
        return new SimpleImageLoadingListener(){
            @Override
            public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
                if (failReason.getCause() instanceof FileNotFoundException && tentativa==1){
                    baixarFoto(usuario.getId(), urlDaFotoMenor(usuario.getImagemUrl()), atualizarFotoBaixada(usuario, usuarioRESTListener, 2));
                }else{
                    usuarioRESTListener.erro();
                }
            }

            @Override
            public void onLoadingComplete(String imageUri, View view, Bitmap bitmap) {
                try {
                    String path = MyApplication.getActivity().getFilesDir().getPath()+ File.separator+"pictures"+File.separator;
                    File file = new File(path);
                    file.mkdirs();
                    File child = new File(path, usuario.getId()+".jpg");
                    child.createNewFile();
                    if (child.exists()) {
                        FileOutputStream outputStream = new FileOutputStream(child);
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                        outputStream.close();
                        usuarioRESTListener.fotoBaixada(child.getPath());
                    }else{
                        Logger.e("Erro ao criar foto\n%s", usuario);
                    }
                }catch (Exception e){
                    e.printStackTrace();
                    Logger.e("Foto com erro: \n%s", usuario);
                }
            }

            @Override
            public void onLoadingCancelled(String imageUri, View view) {
                Logger.i("Download de foto cancelado");
            }

            private String urlDaFotoMenor(String url){
                return url.substring(0,url.length()-6)+"large";
            }
        };
    }

}
