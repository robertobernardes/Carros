package br.com.livroandroid.carros.fragments;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import android.support.v7.view.ActionMode;
import android.support.v7.widget.ShareActionProvider;


import com.squareup.otto.Subscribe;

import org.parceler.Parcels;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import br.com.livroandroid.carros.CarrosApplication;
import br.com.livroandroid.carros.R;
import br.com.livroandroid.carros.activity.CarroActivity;
import br.com.livroandroid.carros.adapter.CarroAdapter;
import br.com.livroandroid.carros.domain.Carro;
import br.com.livroandroid.carros.domain.CarroDB;
import br.com.livroandroid.carros.domain.CarroService;
import livroandroid.lib.utils.AndroidUtils;

import static java.security.AccessController.getContext;

public class CarrosFragment extends BaseFragment {
    protected RecyclerView recyclerView;
    private int tipo;
    private List<Carro> carros;
    private SwipeRefreshLayout swipeLayout;
    private ActionMode actionMode;
    private Intent shareIntent;

    // Método para instanciar esse fragment pelo tipo.
    public static CarrosFragment newInstance(int tipo) {
        Bundle args = new Bundle();
        args.putInt("tipo", tipo);
        CarrosFragment f = new CarrosFragment();
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            // Lê o tipo dos argumentos.
            this.tipo = getArguments().getInt("tipo");
        }

        // Registra a classe para receber eventos.
        CarrosApplication.getInstance().getBus().register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Cancela o recebimento de eventos.
        CarrosApplication.getInstance().getBus().unregister(this);
    }

    @Subscribe
    public void onBusAtualizarListaCarros(String refresh) {
        // Recebeu o evento, atualiza a lista.
        taskCarros(false);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_carros, container, false);
        recyclerView = (RecyclerView) view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setHasFixedSize(true);

        swipeLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipeToRefresh);
        swipeLayout.setOnRefreshListener(OnRefreshListener());
        swipeLayout.setColorSchemeResources(
                R.color.refresh_progress_1,
                R.color.refresh_progress_2,
                R.color.refresh_progress_3);

        return view;
    }

    private SwipeRefreshLayout.OnRefreshListener OnRefreshListener() {
        return new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // Valida se existe conexão ao fazer o gesto Pull to Refresh
                if(AndroidUtils.isNetworkAvailable(getContext())) {
                    // Atualiza ao fazer o gesto Pull to Refresh
                    taskCarros(true);
                } else {
                    swipeLayout.setRefreshing(false);
                    snack(recyclerView, R.string.error_conexao_indisponivel);
                }
            }
        };
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        taskCarros(false);
    }

    private void taskCarros(boolean pullToRefresh) {
        // Busca os carros: Dispara a Task
        startTask("carros", new GetCarrosTask(pullToRefresh),pullToRefresh ? R.id.swipeToRefresh : R.id.progress);
    }

    // Task para buscar os carros
    private class GetCarrosTask implements TaskListener<List<Carro>> {
        public boolean refresh;
        public GetCarrosTask(boolean refresh){
            this.refresh = refresh;
        }
        @Override
        public List<Carro> execute() throws Exception {
            Thread.sleep(500);
            return CarroService.getCarros(getContext(), tipo, refresh);
        }

        @Override
        public void updateView(List<Carro> carros) {
            if (carros != null){
                CarrosFragment.this.carros = carros;
                recyclerView.setAdapter(new CarroAdapter(getContext(), carros, onClickCarro()));
            }
        }

        @Override
        public void onError(Exception e) {
            alert("Ocorreu algum erro ao buscar os dados");
        }

        @Override
        public void onCancelled(String s) {
        }
    }

    private CarroAdapter.CarroOnClickListener onClickCarro() {
        return new CarroAdapter.CarroOnClickListener() {
            @Override
            public void onClickCarro(View view, int idx) {
                Carro c = carros.get(idx);
                if (actionMode == null) {
                    Intent intent = new Intent(getContext(), CarroActivity.class);
                    intent.putExtra("carro", Parcels.wrap(c));
                    startActivity(intent);
                } else { // Se a CAB está ativada
                    // Seleciona o carro
                    c.selected = !c.selected;
                    // Atualiza o título com a quantidade de carros selecionados
                    updateActionModeTitle();
                    // Redesenha a lista
                    recyclerView.getAdapter().notifyDataSetChanged();
                }
            }
            @Override
            public void onLongClickCarro(View view, int idx) {
                if (actionMode != null) { return; }
                // Liga a action bar de contexto (CAB)
                actionMode = getAppCompatActivity().
                        startSupportActionMode(getActionModeCallback());
                Carro c = carros.get(idx);
                c.selected = true; // Seleciona o carro
                // Solicita ao Android para desenhar a lista novamente
                recyclerView.getAdapter().notifyDataSetChanged();
                // Atualiza o título para mostrar a quantidade de carros selecionados
                updateActionModeTitle();
            }
        };
    }

    // Atualiza o título da action bar (CAB)
    private void updateActionModeTitle() {
        if (actionMode != null) {
            actionMode.setTitle("Selecione os carros.");
            actionMode.setSubtitle(null);
            List<Carro> selectedCarros = getSelectedCarros();
            if (selectedCarros.size() == 1) {
                actionMode.setSubtitle("1 carro selecionado");
            } else if (selectedCarros.size() > 1) {
                actionMode.setSubtitle(selectedCarros.size() + " carros selecionados");
            } else {
                //Minha implementação
                actionMode.finish();
            }

            updateShareIntent(selectedCarros);
        }
    }

    // Atualiza a share intent com os carros selecionados
    private void updateShareIntent(List<Carro> selectedCarros) {
        if(shareIntent != null) {
            // Texto com os carros
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Carros: " + selectedCarros);
        }
    }

    // Retorna a lista de carros selecionados
    private List<Carro> getSelectedCarros() {
        List<Carro> list = new ArrayList<Carro>();
        for (Carro c : carros) {
            if (c.selected) { list.add(c); }
        }
        return list;
    }

    private ActionMode.Callback getActionModeCallback() {
        return new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                // Infla o menu específico da action bar de contexto (CAB)
                MenuInflater inflater = getActivity().getMenuInflater();
                inflater.inflate(R.menu.menu_frag_carros_cab, menu);

                MenuItem shareItem = menu.findItem(R.id.action_share);
                ShareActionProvider share = (ShareActionProvider) MenuItemCompat.getActionProvider(shareItem);
                shareIntent = new Intent(Intent.ACTION_SEND);
                //shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.app_name));
                shareIntent.setType("text/*");
                share.setShareIntent(shareIntent);

                return true;
            }
            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return true;
            }
            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                List<Carro> selectedCarros = getSelectedCarros();
                if (item.getItemId() == R.id.action_remove) {
                    CarroDB db = new CarroDB(getContext());
                    try {
                        for (Carro c: selectedCarros) {
                            db.delete(c); // Deleta o carro do banco
                            carros.remove(c); // Remove da lista
                        }
                    } finally {
                        db.close();
                    }
                    snack(recyclerView, "Carros excluídos com sucesso.");

                } else if (item.getItemId() == R.id.action_share) {
                    toast("Compartilhar " + selectedCarros);
                }
                // Encerra o action mode
                mode.finish();
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                // Limpa o estado
                actionMode = null;
                // Configura todos os carros para não selecionados
                for (Carro c : carros) {
                    c.selected = false;
                }
                recyclerView.getAdapter().notifyDataSetChanged();
            }
        };
    }
}
