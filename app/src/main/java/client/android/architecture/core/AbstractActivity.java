package client.android.architecture.core;

import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.TabLayout;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import client.android.R;
import client.android.architecture.custom.CustomTabLayout;
import client.android.architecture.custom.IMainActivity;
import client.android.architecture.custom.Session;
import client.android.dao.service.IDao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public abstract class AbstractActivity extends AppCompatActivity implements IMainActivity {
  // couche [DAO]
  private IDao dao;
  // la session
  protected ISession session;

  // le conteneur des fragments
  protected MyPager mViewPager;
  // la barre d'outils
  private Toolbar toolbar;
  // l'image d'attente
  private ProgressBar loadingPanel;
  // barre d'onglets
  protected TabLayout tabLayout;

  // le gestionnaire de fragments ou sections
  private FragmentPagerAdapter mSectionsPagerAdapter;
  // nom de la classe
  protected String className;
  // mappeur jSON
  private ObjectMapper jsonMapper;

  // constructeur
  public AbstractActivity() {
    // nom de la classe
    className = getClass().getSimpleName();
    // log
    if (IS_DEBUG_ENABLED) {
      Log.d(className, "constructeur");
    }
    // jsonMapper
    jsonMapper = new ObjectMapper();
  }

  // implémentation IMainActivity --------------------------------------------------------------------
  @Override
  public ISession getSession() {
    return session;
  }

  @Override
  public void navigateToView(int position, ISession.Action action) {
    if (IS_DEBUG_ENABLED) {
      Log.d(className, String.format("navigation vers vue %s sur action %s", position, action));
    }
    // affichage nouveau fragment
    mViewPager.setCurrentItem(position);
    // on note l'action en cours lors de ce changement de vue
    session.setAction(action);
  }

  // gestion sauvegarde / restauration de l'activité ------------------------------------
  @Override
  protected void onSaveInstanceState(Bundle outState) {
    // parent
    super.onSaveInstanceState(outState);
    // sauvegarde session sous la forme d'une chaîne jSON
    try {
      outState.putString("session", jsonMapper.writeValueAsString(session));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    // log
    if (IS_DEBUG_ENABLED) {
      try {
        Log.d(className, String.format("onSaveInstanceState session=%s", jsonMapper.writeValueAsString(session)));
      } catch (JsonProcessingException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    // parent
    super.onCreate(savedInstanceState);
    // log
    if (IS_DEBUG_ENABLED) {
      Log.d(className, "onCreate");
    }
    // qq chose à restaurer ?
    if (savedInstanceState != null) {
      // récupération session
      try {
        session = jsonMapper.readValue(savedInstanceState.getString("session"), new TypeReference<Session>() {
        });
      } catch (IOException e) {
        e.printStackTrace();
      }
      // log
      if (IS_DEBUG_ENABLED) {
        try {
          Log.d(className, String.format("onCreate session=%s", jsonMapper.writeValueAsString(session)));
        } catch (JsonProcessingException e) {
          e.printStackTrace();
        }
      }
    } else {
      // session
      session = new Session();
    }
    // couche [DAO]
    dao = getDao();
    if (dao != null) {
      // configuration de la couche [DAO]
      setDebugMode(IS_DEBUG_ENABLED);
      setTimeout(TIMEOUT);
      setDelay(DELAY);
      setBasicAuthentification(IS_BASIC_AUTHENTIFICATION_NEEDED);
    }
    // vue associée
    setContentView(R.layout.activity_main);
    // composants de la vue ---------------------
    // barre d'outils
    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    // image d'attente ?
    if (IS_WAITING_ICON_NEEDED) {
      // on ajoute l'image d'attente
      if (IS_DEBUG_ENABLED) {
        Log.d(className, "adding loadingPanel");
      }
      // création ProgressBar
      loadingPanel = new ProgressBar(this);
      loadingPanel.setVisibility(View.INVISIBLE);
      // ajout du ProgressBar à la barre d'outils
      toolbar.addView(loadingPanel);
    }
    // barre d'onglets ?
    if (ARE_TABS_NEEDED) {
      // on ajoute la barre d'onglets
      if (IS_DEBUG_ENABLED) {
        Log.d(className, "adding tablayout");
      }
      // pas de navigation sur sélection jusqu'à l'affichage d'un fragment
      session.setNavigationOnTabSelectionNeeded(false);
      // création barre d'onglets
      tabLayout = new CustomTabLayout(this);
      tabLayout.setTabTextColors(ContextCompat.getColorStateList(this, R.color.tab_text));
      // ajout de la barre d'onglets à la barre d'application
      AppBarLayout appBarLayout = (AppBarLayout) findViewById(R.id.appbar);
      appBarLayout.addView(tabLayout);
      // gestionnaire d'évt de la barre d'onglets
      tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
        @Override
        public void onTabSelected(TabLayout.Tab tab) {
          // un onglet a été sélectionné
          if (IS_DEBUG_ENABLED) {
            Log.d(className, String.format("onTabSelected n° %s, action=%s, tabCount=%s isNavigationOnTabSelectionNeeded=%s",
              tab.getPosition(), session.getAction(), tabLayout.getTabCount(), session.isNavigationOnTabSelectionNeeded()));
          }
          if (session.isNavigationOnTabSelectionNeeded()) {
            // position de l'onglet
            int position = tab.getPosition();
            // mémoire
            session.setPreviousTab(position);
            // affichage fragment associé ?
            navigateOnTabSelected(position);
          }
        }

        @Override
        public void onTabUnselected(TabLayout.Tab tab) {

        }

        @Override
        public void onTabReselected(TabLayout.Tab tab) {

        }
      });
    }
    // instanciation du gestionnaire de fragments
    mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
    // le conteneur de fragments est associé au gestionnaire de fragments
    // ç-à-d que le fragment n° i du conteneur de fragments est le fragment n° i délivré par le gestionnaire de fragments
    mViewPager = (MyPager) findViewById(R.id.container);
    mViewPager.setAdapter(mSectionsPagerAdapter);
    // on inhibe le swipe entre fragments
    mViewPager.setSwipeEnabled(false);
    // adjacence des fragments
    mViewPager.setOffscreenPageLimit(OFF_SCREEN_PAGE_LIMIT);
    // qu'on associe à notre gestionnaire de fragments
    mViewPager.setAdapter(mSectionsPagerAdapter);
    // on affiche la 1ère vue
    if (session.getAction() == ISession.Action.NONE) {
      navigateToView(getFirstView(), ISession.Action.NONE);
    }
    // on passe la main à l'activité fille
    onCreateActivity();
  }

  @Override
  public void onResume() {
    // parent
    super.onResume();
    if (IS_DEBUG_ENABLED) {
      Log.d(className, "onResume");
    }
    // si restauration, alors il faut restaurer le dernier onglet sélectionné
    if (ARE_TABS_NEEDED && session.getAction() == ISession.Action.RESTORE) {
      tabLayout.getTabAt(session.getPreviousTab()).select();
    }
  }

  // gestion de l'image d'attente ---------------------------------
  public void cancelWaiting() {
    if (loadingPanel != null) {
      loadingPanel.setVisibility(View.INVISIBLE);
    }
  }

  public void beginWaiting() {
    if (loadingPanel != null) {
      loadingPanel.setVisibility(View.VISIBLE);
    }
  }


  // interface IDao -----------------------------------------------------
  @Override
  public void setUrlServiceWebJson(String url) {
    dao.setUrlServiceWebJson(url);
  }

  @Override
  public void setUser(String user, String mdp) {
    dao.setUser(user, mdp);
  }

  @Override
  public void setTimeout(int timeout) {
    dao.setTimeout(timeout);
  }

  @Override
  public void setBasicAuthentification(boolean isBasicAuthentificationNeeded) {
    dao.setBasicAuthentification(isBasicAuthentificationNeeded);
  }

  @Override
  public void setDebugMode(boolean isDebugEnabled) {
    dao.setDebugMode(isDebugEnabled);
  }

  @Override
  public void setDelay(int delay) {
    dao.setDelay(delay);
  }

  // le gestionnaire de fragments --------------------------------
  public class SectionsPagerAdapter extends FragmentPagerAdapter {

    AbstractFragment[] fragments;

    // constructeur
    public SectionsPagerAdapter(FragmentManager fm) {
      super(fm);
      // fragments de la classe fille
      fragments = getFragments();
    }

    // doit rendre le fragment n° i avec ses éventuels arguments
    @Override
    public AbstractFragment getItem(int position) {
      // on rend le fragment
      return fragments[position];
    }

    // rend le nombre de fragments à gérer
    @Override
    public int getCount() {
      return fragments.length;
    }

    // rend le titre du fragment n° position
    @Override
    public CharSequence getPageTitle(int position) {
      return getFragmentTitle(position);
    }
  }

  // classes filles
  protected abstract void onCreateActivity();

  protected abstract IDao getDao();

  protected abstract AbstractFragment[] getFragments();

  protected abstract CharSequence getFragmentTitle(int position);

  protected abstract void navigateOnTabSelected(int position);

  protected abstract int getFirstView();

}
