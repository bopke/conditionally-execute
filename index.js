'use strict';

class ConditionallyExecute {
    constructor() {
        this._onTrue = [];
        this._onFalse = [];
        this.True = true;
    }

    condition(condition) {
        (!!this.True) ? this.True = condition : null;
        return this;
    }

    execute() {
        (!!this.True) ? this._onTrue.forEach((func) => {
                func();
            }) :
            this._onFalse.forEach((func) => {
                func();
            });

    }

    onTrue(func) {
        this._onTrue.push(func);
        return this;
    }

    onFalse(func) {
        this._onFalse.push(func);
        return this;
    }
}

module.exports = ConditionallyExecute;