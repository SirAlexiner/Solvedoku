#include <iostream>
#include <fstream>
#include <algorithm>
#include <vector>
#include <array>
#include <set>
#include <queue>
#include <numeric>

class Sudoku {
private:
    static const int SIZE = 9;
    static const int SUBGRID_SIZE = 3;

    // Confirm that a number is a valid choice for the selected cell (row, col)
    [[nodiscard]] bool is_valid(int row, int col, int num) const {
        // Check row and column
        for (int i = 0; i < SIZE; i++) {
            if (grid[row][i] == num || grid[i][col] == num) {
                return false;
            }
        }

        // Check subgrid
        int startRow = SUBGRID_SIZE * (row / SUBGRID_SIZE);
        int startCol = SUBGRID_SIZE * (col / SUBGRID_SIZE);
        for (int i = 0; i < SUBGRID_SIZE; ++i) {
            for (int j = 0; j < SUBGRID_SIZE; ++j) {
                int r = startRow + i;
                int c = startCol + j;
                if (grid[r][c] == num) {
                    return false;
                }
            }
        }
        return true;
    }


// Initialize the domains for each cell
    void initialize_domains() {
        std::vector<int> numbers(SIZE);
        std::iota(numbers.begin(), numbers.end(), 1);

        for (int row = 0; row < SIZE; ++row) {
            for (int col = 0; col < SIZE; ++col) {
                if (grid[row][col] != 0) {
                    // Assigned cells have a fixed domain
                    domains[row][col] = {grid[row][col]};
                    continue;
                }

                // Unassigned cells can have any valid number
                std::set<int> possible_values;
                std::ranges::copy_if(
                        numbers,
                        std::inserter(possible_values, possible_values.end()),
                        [&](int num) { return is_valid(row, col, num); }
                );
                domains[row][col] = possible_values;
            }
        }
    }

#pragma clang diagnostic push
#pragma ide diagnostic ignored "UnreachableCallsOfFunction"
    // Get the neighbours of a cell (cells in the same row, column, or subgrid)
    static std::vector<std::pair<int, int>> get_neighbours(int row, int col) {
        std::set<std::pair<int, int>> neighbours;

        // Row neighbours
        for (int i = 0; i < SIZE; ++i) {
            if (i != col) {
                neighbours.emplace(row, i);
            }
        }

        // Column neighbours
        for (int i = 0; i < SIZE; ++i) {
            if (i != row) {
                neighbours.emplace(i, col);
            }
        }

        // Subgrid neighbours
        int startRow = SUBGRID_SIZE * (row / SUBGRID_SIZE);
        int startCol = SUBGRID_SIZE * (col / SUBGRID_SIZE);
        for (int i = 0; i < SUBGRID_SIZE; ++i) {
            for (int j = 0; j < SUBGRID_SIZE; ++j) {
                int r = startRow + i;
                int c = startCol + j;
                if (r != row || c != col) {
                    neighbours.emplace(r, c);
                }
            }
        }

        // Convert set to vector
        auto neigeborVector = std::vector<std::pair<int, int>>(neighbours.begin(), neighbours.end());

        return neigeborVector;
    }
#pragma clang diagnostic pop

    // Forward checking: Update domains of neighbouring cells after assignment
    bool forward_check(int row, int col, int value,
                       std::vector<std::tuple<int, int, int>> &arc_domain_changes,
                       std::vector<std::pair<int, int>> &assigned_vars) {
        std::queue<std::pair<int, int>> singleton_queue;

        // Remove 'value' from neighbors' domains
        std::vector<std::pair<int, int>> neighbors = get_neighbours(row, col);

        for (const auto &[n_row, n_col]: neighbors) {
            if (grid[n_row][n_col] != 0 || !domains[n_row][n_col].contains(value))
                continue;

            domains[n_row][n_col].erase(value);
            arc_domain_changes.emplace_back(n_row, n_col, value);

            if (domains[n_row][n_col].empty()) return false; // Conflict detected

            if (domains[n_row][n_col].size() == 1)
                singleton_queue.emplace(n_row, n_col);
        }

        // Propagate singleton domains
        while (!singleton_queue.empty()) {
            auto [s_row, s_col] = singleton_queue.front();
            singleton_queue.pop();

            int singleton_value = *domains[s_row][s_col].begin();
            grid[s_row][s_col] = singleton_value;  // Assign the singleton value
            assigned_vars.emplace_back(s_row, s_col);  // Record the assignment

            std::vector<std::pair<int, int>> s_neighbors = get_neighbours(s_row, s_col);
            for (const auto &[n_row, n_col]: s_neighbors) {
                if (grid[n_row][n_col] != 0 || !domains[n_row][n_col].contains(singleton_value))
                    continue;

                domains[n_row][n_col].erase(singleton_value);
                arc_domain_changes.emplace_back(n_row, n_col, singleton_value);

                if (domains[n_row][n_col].empty()) return false; // Conflict detected

                if (domains[n_row][n_col].size() == 1)
                    singleton_queue.emplace(n_row, n_col);
            }
        }

        return true;
    }

    // Restore domains when backtracking
    void restore_domains(const std::vector<std::tuple<int, int, int>> &domain_changes,
                         const std::vector<std::pair<int, int>> &assigned_vars) {
        // Restore domains
        for (const auto &[row, col, value]: domain_changes) {
            domains[row][col].insert(value);
        }

        // Unassign variables
        for (const auto &[row, col]: assigned_vars) {
            grid[row][col] = 0;
        }
    }


    // Find the cell with the least amount of possible values (MRV heuristic)
    [[nodiscard]] std::pair<int, int> find_mrv_cell() const {
        int min_remaining = SIZE + 1;
        std::pair<int, int> min_mrv_cell = {-1, -1};

        for (int row = 0; row < SIZE; ++row) {
            for (int col = 0; col < SIZE; ++col) {
                if (grid[row][col] != 0)
                    continue;

                auto remaining = static_cast<int>(domains[row][col].size());
                if (remaining >= min_remaining)
                    continue;

                min_remaining = remaining;
                min_mrv_cell = {row, col};

                // Early exit if domain size is 1
                if (remaining == 1)
                    return min_mrv_cell;
            }
        }

        return min_mrv_cell;
    }

    [[nodiscard]] int count_conflicts(int row, int col, int num) const {
        int conflicts = 0;

        for (int i = 0; i < SIZE; i++) {
            if (i != col && !grid[row][i] && is_valid(row, i, num)) {
                conflicts++;
            }
            if (i != row && !grid[i][col] && is_valid(i, col, num)) {
                conflicts++;
            }

            int box_row = SUBGRID_SIZE * (row / SUBGRID_SIZE) + i / SUBGRID_SIZE;
            int box_col = SUBGRID_SIZE * (col / SUBGRID_SIZE) + i % SUBGRID_SIZE;
            if ((box_row != row || box_col != col) && !grid[box_row][box_col] &&
                is_valid(box_row, box_col, num)) {
                conflicts++;
            }
        }

        return conflicts;
    }

    [[nodiscard]] std::vector<std::pair<int, int>>
    sort_domain_MRV(int row, int col, const std::vector<int> &values) const {
        std::vector<std::pair<int, int>> nums;

        for (int value: values) {

            int conflicts = count_conflicts(row, col, value);
            nums.emplace_back(conflicts, value);
        }

        std::sort(nums.rbegin(), nums.rend());
        return nums;
    }

public:
    int recursion = -1;

    // The domains for each cell
    std::array<std::array<std::set<int>, SIZE>, SIZE> domains{};

    // The Sudoku grid
    std::array<std::array<int, SIZE>, SIZE> grid{};

    Sudoku() {
        grid.fill(std::array<int, SIZE>{});
    }

    int validateLoadHints(std::array<std::array<int, SIZE>, SIZE> givenHints) {
        int countHints = 0;
        for (int row = 0; row < SIZE; ++row) {
            for (int col = 0; col < SIZE; ++col) {
                if (givenHints[row][col] == 0) {
                    grid[row][col] = givenHints[row][col];
                    continue;
                }

                countHints++;
                if (!is_valid(row, col, givenHints[row][col])) {
                    return -1; // Invalid puzzle configuration
                }
                grid[row][col] = givenHints[row][col];
            }
        }

        initialize_domains(); 

        if (countHints >= 17) return 1; // Valid puzzle with sufficient hints
        else return 0; // Not enough hints
    }

    // Solve the Sudoku using backtracking with forward checking
    bool solve() {
        recursion++;
        int row;
        int col;

        row = find_mrv_cell().first;
        col = find_mrv_cell().second;

        if (row == -1 || col == -1) return true;

        // Get the values from the domain of the selected cell
        std::vector<int> values(domains[row][col].begin(), domains[row][col].end());
        auto mcv_values = sort_domain_MRV(row, col, values);

        return std::ranges::any_of(mcv_values, [&](const auto &value) {
            grid[row][col] = value.second;

            // Save domain changes and assigned variables to restore later if needed
            std::vector<std::tuple<int, int, int>> forward_domain_changes;
            std::vector<std::tuple<int, int, int>> arc_domain_changes;
            std::vector<std::pair<int, int>> assigned_vars;
            assigned_vars.emplace_back(row, col);  // Record the assignment

            // Forward Checking with Singleton Propagation
            if (forward_check(row, col, value.second, arc_domain_changes, assigned_vars) &&
                solve())
                return true;

            // Backtrack
            restore_domains(arc_domain_changes, assigned_vars);  // Restore arc-consistency changes
            restore_domains(forward_domain_changes, assigned_vars);
            grid[row][col] = 0;  // Unassign the current cell

            return false;
        });
    }

    void print() const {
        for (int row = 0; row < SIZE; ++row) {
            std::cout << "       ";
            if (row % SUBGRID_SIZE == 0 && row != 0) std::cout << "---------------------" << std::endl << "       ";
            for (int col = 0; col < SIZE; ++col) {
                if (col % SUBGRID_SIZE == 0 && col != 0) std::cout << "| ";
                if (grid[row][col]) {
                    std::cout << grid[row][col] << " ";
                } else {
                    std::cout << ". ";
                }
            }
            std::cout << std::endl;
        }
        std::cout << std::endl;
    }
};