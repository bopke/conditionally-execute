'use strict';

require('mocha');

const assert = require('assert');
const ConditionallyExecute = require('./');

describe('basic functionality', function () {
    it('should execute onFalse when condition is falsy', function (done) {
        let condExec = new ConditionallyExecute();
        let wasOnTrueExecuted = false;
        let wasOnFalseExecuted = false;
        condExec.onTrue(() => {
            wasOnTrueExecuted = true;
        });
        condExec.onFalse(() => {
            wasOnFalseExecuted = true;
        });
        condExec.condition(1 === 2).execute();
        assert.equal(wasOnFalseExecuted, true);
        assert.equal(wasOnTrueExecuted, false);
        done();
    });

    it('should execute onTrue when condition is truthy', function (done) {
        let condExec = new ConditionallyExecute();
        let wasOnTrueExecuted = false;
        let wasOnFalseExecuted = false;
        condExec.onTrue(() => {
            wasOnTrueExecuted = true;
        });
        condExec.onFalse(() => {
            wasOnFalseExecuted = true;
        });
        condExec.condition(1 === 1).execute();
        assert.equal(wasOnFalseExecuted, false);
        assert.equal(wasOnTrueExecuted, true);
        done();
    });

    it('should execute onTrue when there is no conditions set', function (done) {
        let condExec = new ConditionallyExecute();
        let wasOnTrueExecuted = false;
        let wasOnFalseExecuted = false;
        condExec.onTrue(() => {
            wasOnTrueExecuted = true;
        });
        condExec.onFalse(() => {
            wasOnFalseExecuted = true;
        });
        condExec.execute();
        assert.equal(wasOnFalseExecuted, false);
        assert.equal(wasOnTrueExecuted, true);
        done();
    });
});

describe('extended functionality', function () {
    it('should execute all onFalse functions when condition is falsy', function (done) {
        let condExec = new ConditionallyExecute();
        let wasOnTrueExecuted = false;
        let wasOnFalseExecuted = false;
        let wasSecondOnFalseExecuted = false;
        condExec.onTrue(() => {
            wasOnTrueExecuted = true;
        });
        condExec.onFalse(() => {
            wasOnFalseExecuted = true;
        });
        condExec.onFalse(() => {
            wasSecondOnFalseExecuted = true;
        })
        condExec.condition(1 === 2).execute();
        assert.equal(wasOnFalseExecuted, true);
        assert.equal(wasSecondOnFalseExecuted, true);
        assert.equal(wasOnTrueExecuted, false);
        done();
    });

    it('should execute all onTrue functions when condition is truthy', function (done) {
        let condExec = new ConditionallyExecute();
        let wasOnTrueExecuted = false;
        let wasSecondOnTrueExecuted = false;
        let wasOnFalseExecuted = false;
        condExec.onTrue(() => {
            wasOnTrueExecuted = true;
        });
        condExec.onTrue(() => {
            wasSecondOnTrueExecuted = true;
        })
        condExec.onFalse(() => {
            wasOnFalseExecuted = true;
        });
        condExec.execute();
        assert.equal(wasOnFalseExecuted, false);
        assert.equal(wasOnTrueExecuted, true);
        assert.equal(wasSecondOnTrueExecuted, true);
        done();
    });

    it('should execute all onTrue functions when there is no conditions set', function (done) {
        let condExec = new ConditionallyExecute();
        let wasOnTrueExecuted = false;
        let wasSecondOnTrueExecuted = false;
        let wasOnFalseExecuted = false;
        condExec.onTrue(() => {
            wasOnTrueExecuted = true;
        });
        condExec.onTrue(() => {
            wasSecondOnTrueExecuted = true;
        })
        condExec.onFalse(() => {
            wasOnFalseExecuted = true;
        });
        condExec.execute();
        assert.equal(wasOnFalseExecuted, false);
        assert.equal(wasOnTrueExecuted, true);
        assert.equal(wasSecondOnTrueExecuted, true);
        done();
    });
});