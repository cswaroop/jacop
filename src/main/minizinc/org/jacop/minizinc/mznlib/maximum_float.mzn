%-----------------------------------------------------------------------------%
% Constrains 'm' to be the maximum of the values in 'x'.
% Assumptions: |x| > 0.
%-----------------------------------------------------------------------------%

predicate maximum_float(var float: m, array[int] of var float: x) =
    % let { int: l = min(index_set(x)),
    %       int: u = max(index_set(x)),
    %       float: ly = lb_array(x),
    %       float: uy = ub_array(x),
    %       array[l..u] of var ly..uy: y } in
    % y[l] = x[l] /\
    % m = y[u] /\
    % forall (i in l+1 .. u) ( y[i] == max(x[i],y[i-1]) );

    jacop_float_maximum(x, m);

predicate jacop_float_maximum(array[int] of var float: x, var float: m);
